# https://docs.python.org/ko/3/reference/datamodel.html#special-method-lookup
class Meta(type):
    def __getattribute__(*args):
        print("Metaclass getattribute invoked")
        return type.__getattribute__(*args)

class C(object, metaclass=Meta):
    def __len__(self):
        print("Special method invoked")
        return 10
    def __getattribute__(*args):
        print("Class getattribute invoked")
        return object.__getattribute__(*args)

c = C()

print(c.__len__())                 # Explicit lookup via instance
# Class getattribute invoked
# Special method invoked
# 10

print(type(c).__len__(c))          # Explicit lookup via type
# Metaclass getattribute invoked
# Special method invoked
# 10

print(len(c))                      # Implicit lookup
# Special method invoked
# 10