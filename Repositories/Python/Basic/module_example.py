print(f"{__name__} loaded") # __main__ loaded

print('hello')

import test_module # test_module loaded

print(test_module) # Verbose test_module

test_module.old_function() # old_function called

print(dir(test_module)) # ['large_module', 'new_function', 'old_function']

print(test_module.tmp) # Default Value
test_module.tmp = 'new value' # Setting tmp...
print(test_module.tmp) # new value

test_module.large_module.HeavyClass # large_module loaded