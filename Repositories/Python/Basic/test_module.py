print(f"{__name__} loaded")

import sys
import importlib
from warnings import warn
from types import ModuleType


# Deprecated range start
deprecated_names = ["old_function",]

def _deprecated_old_function():
    print("old_function called")
# Deprecated range end


submodule_names = ["large_module",]
function_names = ["new_function",]

# Function range start
def new_function():
    print("new_function called")
# Function range end


# Special attribute range start
def __getattr__(name):
    if name in deprecated_names:
        warn(f"{name} is deprecated", DeprecationWarning)
        return globals()[f"_deprecated_{name}"]
    if name in submodule_names:
        return importlib.import_module(name)
    if name in function_names:
        return globals()[name]
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")

def __dir__():
    return sorted(deprecated_names + submodule_names + function_names)

class VerboseModule(ModuleType):
    def __repr__(self):
        return f'Verbose {self.__name__}'

    def __setattr__(self, attr, value):
        print(f'Setting {attr}...')
        super().__setattr__(attr, value)

    def __getattr__(self, name):
        return 'Default Value'

sys.modules[__name__].__class__ = VerboseModule
# Special attribute range end