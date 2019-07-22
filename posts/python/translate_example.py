reverse_map = {(ord('a')+c): chr(ord('z')-c) for c in range(0, 26)}
alphabet = 'abcdefghijklmnopqrstuvwxyz'
trans = str.maketrans(alphabet, alphabet[::-1])

print(reverse_map)
print(trans)

s = 'apple PIE'
print(s.translate(reverse_map))
print(s.translate(trans))