class Person:
    """A simple class for describing a person"""
    regident_id = 0 # class variable shared by all instances

    def __init__(self, name = ""):
        self.name = name # instance variable unique to each instance

    def show(self):
        print(self.name)

p = Person("DK")
p2 = Person("wiz")
p.show()
f = p.show
p.name = "KD"
f()

Person.height = 175
p.weight = 70
print("'weight' in dir(p) : ", 'weight' in dir(p))
print("'weight' in dir(p2) : ", 'weight' in dir(p2))
print("'height' in dir(p) : ", 'height' in dir(p))
print("'height' in dir(p2) : ", 'height' in dir(p2))

print(p.__class__)
print(p2.__class__)