# ProjectBuildFileParser adds:
#
# from __future__ import with_statement
# import sys
# sys.path.insert(0, "/path/to/build/pywatchman")
# sys.path.insert(0, "/path/to/pathlib")

import __builtin__
import __future__
import functools
import imp
import inspect
import json
from pathlib import Path, PureWindowsPath, PurePath
import optparse
import os
import os.path
import subprocess
import sys


# When build files are executed, the functions in this file tagged with
# @provide_for_build will be provided in the build file's local symbol table.
#
# When these functions are called from a build file, they will be passed
# a keyword parameter, build_env, which is a object with information about
# the environment of the build file which is currently being processed.
# It contains the following attributes:
#
# "dirname" - The directory containing the build file.
#
# "base_path" - The base path of the build file.

BUILD_FUNCTIONS = []


class SyncCookieState(object):
    """
    Process-wide state used to enable Watchman sync cookies only on
    the first query issued.
    """

    def __init__(self):
        self.use_sync_cookies = True


class BuildContextType(object):

    """
    Identifies the type of input file to the processor.
    """

    BUILD_FILE = 'build_file'
    INCLUDE = 'include'


class BuildFileContext(object):
    """
    The build context used when processing a build file.
    """

    type = BuildContextType.BUILD_FILE

    def __init__(self, base_path, dirname, allow_empty_globs, watchman_client,
                 watchman_watch_root, watchman_project_prefix, sync_cookie_state,
                 watchman_error):
        self.globals = {}
        self.includes = set()
        self.base_path = base_path
        self.dirname = dirname
        self.allow_empty_globs = allow_empty_globs
        self.watchman_client = watchman_client
        self.watchman_watch_root = watchman_watch_root
        self.watchman_project_prefix = watchman_project_prefix
        self.sync_cookie_state = sync_cookie_state
        self.watchman_error = watchman_error
        self.rules = {}


class IncludeContext(object):
    """
    The build context used when processing an include.
    """

    type = BuildContextType.INCLUDE

    def __init__(self):
        self.globals = {}
        self.includes = set()


class LazyBuildEnvPartial(object):
    """Pairs a function with a build environment in which it will be executed.

    Note that while the function is specified via the constructor, the build
    environment must be assigned after construction, for the build environment
    currently being used.

    To call the function with its build environment, use the invoke() method of
    this class, which will forward the arguments from invoke() to the
    underlying function.
    """

    def __init__(self, func):
        self.func = func
        self.build_env = None

    def invoke(self, *args, **kwargs):
        """Invokes the bound function injecting 'build_env' into **kwargs."""
        updated_kwargs = kwargs.copy()
        updated_kwargs.update({'build_env': self.build_env})
        return self.func(*args, **updated_kwargs)


def provide_for_build(func):
    BUILD_FUNCTIONS.append(func)
    return func


def add_rule(rule, build_env):
    assert build_env.type == BuildContextType.BUILD_FILE, (
        "Cannot use `{}()` at the top-level of an included file."
        .format(rule['buck.type']))

    # Include the base path of the BUILD file so the reader consuming this
    # JSON will know which BUILD file the rule came from.
    if 'name' not in rule:
        raise ValueError(
            'rules must contain the field \'name\'.  Found %s.' % rule)
    rule_name = rule['name']
    if rule_name in build_env.rules:
        raise ValueError('Duplicate rule definition found.  Found %s and %s' %
                         (rule, build_env.rules[rule_name]))
    rule['buck.base_path'] = build_env.base_path
    build_env.rules[rule_name] = rule


class memoized(object):
    '''Decorator. Caches a function's return value each time it is called.
    If called later with the same arguments, the cached value is returned
    (not reevaluated).
    '''
    def __init__(self, func):
        self.func = func
        self.cache = {}

    def __call__(self, *args):
        args_key = repr(args)
        if args_key in self.cache:
            return self.cache[args_key]
        else:
            value = self.func(*args)
            self.cache[args_key] = value
            return value

    def __repr__(self):
        '''Return the function's docstring.'''
        return self.func.__doc__

    def __get__(self, obj, objtype):
        '''Support instance methods.'''
        return functools.partial(self.__call__, obj)


@provide_for_build
def glob(includes, excludes=[], include_dotfiles=False, build_env=None, search_base=None):
    assert build_env.type == BuildContextType.BUILD_FILE, (
        "Cannot use `glob()` at the top-level of an included file.")
    # Ensure the user passes lists of strings rather than just a string.
    assert not isinstance(includes, basestring), \
        "The first argument to glob() must be a list of strings."
    assert not isinstance(excludes, basestring), \
        "The excludes argument must be a list of strings."

    results = None
    if not includes:
        results = []
    elif build_env.watchman_client:
        try:
            results = glob_watchman(
                includes,
                excludes,
                include_dotfiles,
                build_env.base_path,
                build_env.watchman_watch_root,
                build_env.watchman_project_prefix,
                build_env.sync_cookie_state,
                build_env.watchman_client)
        except build_env.watchman_error, e:
            print >>sys.stderr, 'Watchman error, falling back to slow glob: ' + str(e)
            try:
                build_env.watchman_client.close()
            except:
                pass
            build_env.watchman_client = None

    if results is None:
        if search_base is None:
            search_base = Path(build_env.dirname)

        results = glob_internal(
            includes,
            excludes,
            include_dotfiles,
            search_base)
    assert build_env.allow_empty_globs or results, (
        "glob(includes={includes}, excludes={excludes}, include_dotfiles={include_dotfiles}) " +
        "returned no results.  (allow_empty_globs is set to false in the Buck " +
        "configuration)").format(
            includes=includes,
            excludes=excludes,
            include_dotfiles=include_dotfiles)

    return results


def merge_maps(*header_maps):
    result = {}
    for header_map in header_maps:
        for key in header_map:
            if key in result and result[key] != header_map[key]:
                assert False, 'Conflicting header files in header search paths. ' + \
                              '"%s" maps to both "%s" and "%s".' \
                              % (key, result[key], header_map[key])

            result[key] = header_map[key]

    return result


def single_subdir_glob(dirpath, glob_pattern, excludes=[], prefix=None, build_env=None,
                       search_base=None):
    results = {}
    files = glob([os.path.join(dirpath, glob_pattern)],
                 excludes=excludes,
                 build_env=build_env,
                 search_base=search_base)
    for f in files:
        if dirpath:
            key = f[len(dirpath) + 1:]
        else:
            key = f
        if prefix:
            # `f` is a string, but we need to create correct platform-specific Path.
            # Using Path straight away won't work because it will use host's class, which is Posix
            # on OS X. For the same reason we can't use os.path.join.
            # Here we try to understand if we're running Windows test, then use WindowsPath
            # to build up the key with prefix, allowing test to pass.
            cls = PureWindowsPath if "\\" in f else PurePath
            key = str(cls(prefix) / cls(key))
        results[key] = f

    return results


@provide_for_build
def subdir_glob(glob_specs, excludes=[], prefix=None, build_env=None, search_base=None):
    """
    Given a list of tuples, the form of (relative-sub-directory, glob-pattern),
    return a dict of sub-directory relative paths to full paths.  Useful for
    defining header maps for C/C++ libraries which should be relative the given
    sub-directory.

    If prefix is not None, prepends it it to each key in the dictionary.
    """

    results = []

    for dirpath, glob_pattern in glob_specs:
        results.append(
            single_subdir_glob(dirpath, glob_pattern, excludes, prefix, build_env, search_base))

    return merge_maps(*results)


def format_watchman_query_params(includes, excludes, include_dotfiles, relative_root):
    match_exprs = ["allof", "exists", ["anyof", ["type", "f"], ["type", "l"]]]
    match_flags = {}
    if include_dotfiles:
        match_flags["includedotfiles"] = True
    if includes:
        match_exprs.append(
            ["anyof"] + [["match", i, "wholename", match_flags] for i in includes])
    if excludes:
        match_exprs.append(
            ["not",
                ["anyof"] + [["match", x, "wholename", match_flags] for x in excludes]])

    return {
        "relative_root": relative_root,
        # Explicitly pass an empty path so Watchman queries only the tree of files
        # starting at base_path.
        "path": [''],
        "fields": ["name"],
        "expression": match_exprs,
    }


@memoized
def glob_watchman(includes, excludes, include_dotfiles, base_path, watchman_watch_root,
                  watchman_project_prefix, sync_cookie_state, watchman_client):
    assert includes, "The includes argument must be a non-empty list of strings."

    if watchman_project_prefix:
        relative_root = os.path.join(watchman_project_prefix, base_path)
    else:
        relative_root = base_path
    query_params = format_watchman_query_params(
        includes, excludes, include_dotfiles, relative_root)

    # Sync cookies cause a massive overhead when issuing thousands of
    # glob queries.  Only enable them (by not setting sync_timeout to 0)
    # for the very first request issued by this process.
    if sync_cookie_state.use_sync_cookies:
        sync_cookie_state.use_sync_cookies = False
    else:
        query_params["sync_timeout"] = 0

    query = ["query", watchman_watch_root, query_params]
    res = watchman_client.query(*query)
    if res.get('warning'):
        print >> sys.stderr, 'Watchman warning from query {}: {}'.format(
            query,
            res.get('warning'))
    result = res.get('files', [])
    return sorted(result)


def glob_internal(includes, excludes, include_dotfiles, search_base):

    def includes_iterator():
        for pattern in includes:
            for path in search_base.glob(pattern):
                # TODO(user): Handle hidden files on Windows.
                if path.is_file() and (include_dotfiles or not path.name.startswith('.')):
                    yield path.relative_to(search_base)

    def is_special(pat):
        return "*" in pat or "?" in pat or "[" in pat

    non_special_excludes = set()
    match_excludes = set()
    for pattern in excludes:
        if is_special(pattern):
            match_excludes.add(pattern)
        else:
            non_special_excludes.add(pattern)

    def exclusion(path):
        if path.as_posix() in non_special_excludes:
            return True
        for pattern in match_excludes:
            result = path.match(pattern, match_entire=True)
            if result:
                return True
        return False

    return sorted(set([str(p) for p in includes_iterator() if not exclusion(p)]))


@provide_for_build
def get_base_path(build_env=None):
    """Get the base path to the build file that was initially evaluated.

    This function is intended to be used from within a build defs file that
    likely contains macros that could be called from any build file.
    Such macros may need to know the base path of the file in which they
    are defining new build rules.

    Returns: a string, such as "java/com/facebook". Note there is no
             trailing slash. The return value will be "" if called from
             the build file in the root of the project.
    """
    assert build_env.type == BuildContextType.BUILD_FILE, (
        "Cannot use `get_base_path()` at the top-level of an included file.")
    return build_env.base_path


@provide_for_build
def add_deps(name, deps=[], build_env=None):
    assert build_env.type == BuildContextType.BUILD_FILE, (
        "Cannot use `add_deps()` at the top-level of an included file.")

    if name not in build_env.rules:
        raise ValueError(
            'Invoked \'add_deps\' on non-existent rule %s.' % name)

    rule = build_env.rules[name]
    if 'deps' not in rule:
        raise ValueError(
            'Invoked \'add_deps\' on rule %s that has no \'deps\' field'
            % name)
    rule['deps'] = rule['deps'] + deps


class BuildFileProcessor(object):

    def __init__(self, project_root, watchman_watch_root, watchman_project_prefix, build_file_name,
                 allow_empty_globs, watchman_client, watchman_error, implicit_includes=[],
                 extra_funcs=[]):
        self._cache = {}
        self._build_env_stack = []
        self._sync_cookie_state = SyncCookieState()

        self._project_root = project_root
        self._watchman_watch_root = watchman_watch_root
        self._watchman_project_prefix = watchman_project_prefix
        self._build_file_name = build_file_name
        self._implicit_includes = implicit_includes
        self._allow_empty_globs = allow_empty_globs
        self._watchman_client = watchman_client
        self._watchman_error = watchman_error

        lazy_functions = {}
        for func in BUILD_FUNCTIONS + extra_funcs:
            func_with_env = LazyBuildEnvPartial(func)
            lazy_functions[func.__name__] = func_with_env
        self._functions = lazy_functions

    def _merge_globals(self, mod, dst):
        """
        Copy the global definitions from one globals dict to another.

        Ignores special attributes and attributes starting with '_', which
        typically denote module-level private attributes.
        """

        hidden = set([
            'include_defs',
        ])

        keys = getattr(mod, '__all__', mod.__dict__.keys())

        for key in keys:
            if not key.startswith('_') and key not in hidden:
                dst[key] = mod.__dict__[key]

    def _update_functions(self, build_env):
        """
        Updates the build functions to use the given build context when called.
        """

        for function in self._functions.itervalues():
            function.build_env = build_env

    def install_builtins(self, namespace):
        """
        Installs the build functions, by their name, into the given namespace.
        """

        for name, function in self._functions.iteritems():
            namespace[name] = function.invoke

    def _get_include_path(self, name):
        """
        Resolve the given include def name to a full path.
        """

        # Find the path from the include def name.
        if not name.startswith('//'):
            raise ValueError(
                'include_defs argument "%s" must begin with //' % name)
        relative_path = name[2:]
        return os.path.join(self._project_root, relative_path)

    def _include_defs(self, name, implicit_includes=[]):
        """
        Pull the named include into the current caller's context.

        This method is meant to be installed into the globals of any files or
        includes that we process.
        """

        # Grab the current build context from the top of the stack.
        build_env = self._build_env_stack[-1]

        # Resolve the named include to its path and process it to get its
        # build context and module.
        path = self._get_include_path(name)
        inner_env, mod = self._process_include(
            path,
            implicit_includes=implicit_includes)

        # Look up the caller's stack frame and merge the include's globals
        # into it's symbol table.
        frame = inspect.currentframe()
        while frame.f_globals['__name__'] == __name__:
            frame = frame.f_back
        self._merge_globals(mod, frame.f_globals)

        # Pull in the include's accounting of its own referenced includes
        # into the current build context.
        build_env.includes.add(path)
        build_env.includes.update(inner_env.includes)

    def _push_build_env(self, build_env):
        """
        Set the given build context as the current context.
        """

        self._build_env_stack.append(build_env)
        self._update_functions(build_env)

    def _pop_build_env(self):
        """
        Restore the previous build context as the current context.
        """

        self._build_env_stack.pop()
        if self._build_env_stack:
            self._update_functions(self._build_env_stack[-1])

    def _process(self, build_env, path, implicit_includes=[]):
        """
        Process a build file or include at the given path.
        """

        # First check the cache.
        cached = self._cache.get(path)
        if cached is not None:
            return cached

        # Install the build context for this input as the current context.
        self._push_build_env(build_env)

        # The globals dict that this file will be executed under.
        default_globals = {}

        # Install the 'include_defs' function into our global object.
        default_globals['include_defs'] = functools.partial(
            self._include_defs,
            implicit_includes=implicit_includes)

        # If any implicit includes were specified, process them first.
        for include in implicit_includes:
            include_path = self._get_include_path(include)
            inner_env, mod = self._process_include(include_path)
            self._merge_globals(mod, default_globals)
            build_env.includes.add(include_path)
            build_env.includes.update(inner_env.includes)

        # Build a new module for the given file, using the default globals
        # created above.
        module = imp.new_module(path)
        module.__file__ = path
        module.__dict__.update(default_globals)

        with open(path) as f:
            contents = f.read()

        # Enable absolute imports.  This prevents the compiler from trying to
        # do a relative import first, and warning that this module doesn't
        # exist in sys.modules.
        future_features = __future__.absolute_import.compiler_flag
        code = compile(contents, path, 'exec', future_features, 1)
        exec(code, module.__dict__)

        # Restore the previous build context.
        self._pop_build_env()

        self._cache[path] = build_env, module
        return build_env, module

    def _process_include(self, path, implicit_includes=[]):
        """
        Process the include file at the given path.
        """

        build_env = IncludeContext()
        return self._process(
            build_env,
            path,
            implicit_includes=implicit_includes)

    def _process_build_file(self, path, implicit_includes=[]):
        """
        Process the build file at the given path.
        """

        # Create the build file context, including the base path and directory
        # name of the given path.
        relative_path_to_build_file = os.path.relpath(
            path, self._project_root).replace('\\', '/')
        len_suffix = -len('/' + self._build_file_name)
        base_path = relative_path_to_build_file[:len_suffix]
        dirname = os.path.dirname(path)
        build_env = BuildFileContext(
            base_path,
            dirname,
            self._allow_empty_globs,
            self._watchman_client,
            self._watchman_watch_root,
            self._watchman_project_prefix,
            self._sync_cookie_state,
            self._watchman_error)

        return self._process(
            build_env,
            path,
            implicit_includes=implicit_includes)

    def process(self, path):
        """
        Process a build file returning a dict of it's rules and includes.
        """
        build_env, mod = self._process_build_file(
            os.path.join(self._project_root, path),
            implicit_includes=self._implicit_includes)
        values = build_env.rules.values()
        values.append({"__includes": [path] + sorted(build_env.includes)})
        return values


def cygwin_adjusted_path(path):
    if sys.platform == 'cygwin':
        return subprocess.check_output(['cygpath', path]).rstrip()
    else:
        return path

# Inexplicably, this script appears to run faster when the arguments passed
# into it are absolute paths. However, we want the "buck.base_path" property
# of each rule to be printed out to be the base path of the build target that
# identifies the rule. That means that when parsing a BUILD file, we must know
# its path relative to the root of the project to produce the base path.
#
# To that end, the first argument to this script must be an absolute path to
# the project root.  It must be followed by one or more absolute paths to
# BUILD files under the project root.  If no paths to BUILD files are
# specified, then it will traverse the project root for BUILD files, excluding
# directories of generated files produced by Buck.
#
# All of the build rules that are parsed from the BUILD files will be printed
# to stdout by a JSON parser. That means that printing out other information
# for debugging purposes will likely break the JSON parsing, so be careful!


def main():
    # Our parent expects to read JSON from our stdout, so if anyone
    # uses print, buck will complain with a helpful "but I wanted an
    # array!" message and quit.  Redirect stdout to stderr so that
    # doesn't happen.  Actually dup2 the file handle so that writing
    # to file descriptor 1, os.system, and so on work as expected too.

    to_parent = os.fdopen(os.dup(sys.stdout.fileno()), 'a')
    os.dup2(sys.stderr.fileno(), sys.stdout.fileno())

    parser = optparse.OptionParser()
    parser.add_option(
        '--project_root',
        action='store',
        type='string',
        dest='project_root')
    parser.add_option(
        '--build_file_name',
        action='store',
        type='string',
        dest="build_file_name")
    parser.add_option(
        '--allow_empty_globs',
        action='store_true',
        dest='allow_empty_globs',
        help='Tells the parser not to raise an error when glob returns no results.')
    parser.add_option(
        '--use_watchman_glob',
        action='store_true',
        dest='use_watchman_glob',
        help='Invokes `watchman query` to get lists of files instead of globbing in-process.')
    parser.add_option(
        '--watchman_watch_root',
        action='store',
        type='string',
        dest='watchman_watch_root',
        help='Path to root of watchman watch as returned by `watchman watch-project`.')
    parser.add_option(
        '--watchman_project_prefix',
        action='store',
        type='string',
        dest='watchman_project_prefix',
        help='Relative project prefix as returned by `watchman watch-project`.')
    parser.add_option(
        '--watchman_query_timeout_ms',
        action='store',
        type='int',
        dest='watchman_query_timeout_ms',
        help='Maximum time in milliseconds to wait for watchman query to respond.')
    parser.add_option(
        '--include',
        action='append',
        dest='include')
    (options, args) = parser.parse_args()

    # Even though project_root is absolute path, it may not be concise. For
    # example, it might be like "C:\project\.\rule".
    #
    # Under cygwin, the project root will be invoked from buck as C:\path, but
    # the cygwin python uses UNIX-style paths. They can be converted using
    # cygpath, which is necessary because abspath will treat C:\path as a
    # relative path.
    options.project_root = cygwin_adjusted_path(options.project_root)
    project_root = os.path.abspath(options.project_root)

    watchman_client = None
    watchman_error = None
    output_format = 'JSON'
    output_encode = lambda val: json.dumps(val, sort_keys=True)
    if options.use_watchman_glob:
        try:
            # pywatchman may not be built, so fall back to non-watchman
            # in that case.
            import pywatchman
            client_args = {}
            if options.watchman_query_timeout_ms is not None:
                # pywatchman expects a timeout as a nonnegative floating-point
                # value in seconds.
                client_args['timeout'] = max(0.0, options.watchman_query_timeout_ms / 1000.0)
            watchman_client = pywatchman.client(**client_args)
            watchman_error = pywatchman.WatchmanError
            output_format = 'BSER'
            output_encode = lambda val: pywatchman.bser.dumps(val)
        except ImportError, e:
            # TODO(agallagher): Restore this when the PEX builds pywatchman.
            # print >> sys.stderr, \
            #     'Could not import pywatchman (sys.path {}): {}'.format(
            #         sys.path,
            #         repr(e))
            pass

    buildFileProcessor = BuildFileProcessor(
        project_root,
        options.watchman_watch_root,
        options.watchman_project_prefix,
        options.build_file_name,
        options.allow_empty_globs,
        watchman_client,
        watchman_error,
        implicit_includes=options.include or [])

    buildFileProcessor.install_builtins(__builtin__.__dict__)

    to_parent.write(output_format + '\n')
    to_parent.flush()

    for build_file in args:
        build_file = cygwin_adjusted_path(build_file)
        values = buildFileProcessor.process(build_file)
        to_parent.write(output_encode(values))
        to_parent.flush()

    # "for ... in sys.stdin" in Python 2.x hangs until stdin is closed.
    for build_file in iter(sys.stdin.readline, ''):
        build_file = cygwin_adjusted_path(build_file)
        values = buildFileProcessor.process(build_file.rstrip())
        to_parent.write(output_encode(values))
        to_parent.flush()

    # Python tries to flush/close stdout when it quits, and if there's a dead
    # pipe on the other end, it will spit some warnings to stderr. This breaks
    # tests sometimes. Prevent that by explicitly catching the error.
    try:
        to_parent.close()
    except IOError:
        pass
