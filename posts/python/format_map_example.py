class Default(dict):
    def __missing__(self, key):
        return key

print('{name} was born in {country}'.format_map(Default(name='Guido')))
