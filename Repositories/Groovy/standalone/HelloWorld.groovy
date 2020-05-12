def map = [:]

map.' a b c ' = 'abc'
assert map." a b c " == 'abc'

def firstName = "Homer"
map."Simpson-${firstName}" = "Homer Simpson"
assert map.'Simpson-Homer' == "Homer Simpson"