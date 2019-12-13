# 출처: https://docs.python.org/3/library/stdtypes.html#str.format_map
class Default(dict):
    def __missing__(self, key):
        return key

print('{name} was born in {country}'.format_map(Default(name='Guido')))
# Guido was born in country