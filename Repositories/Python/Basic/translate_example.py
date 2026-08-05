reverse_map = {(ord('a')+c): chr(ord('z')-c) for c in range(0, 26)}
alphabet = 'abcdefghijklmnopqrstuvwxyz'
trans = str.maketrans(alphabet, alphabet[::-1])

print(reverse_map)
# {97: 'z', 98: 'y', 99: 'x', 100: 'w', 101: 'v', 102: 'u', 103: 't', 104: 's', 105: 'r', 106:'q', 107: 'p', 108: 'o', 109: 'n', 110: 'm', 111: 'l', 112: 'k', 113: 'j', 114: 'i', 115: 'h', 116: 'g', 117: 'f', 118: 'e', 119: 'd', 120: 'c', 121: 'b', 122: 'a'}
print(trans)
# {97: 122, 98: 121, 99: 120, 100: 119, 101: 118, 102: 117, 103: 116, 104: 115, 105: 114, 106:113, 107: 112, 108: 111, 109: 110, 110: 109, 111: 108, 112: 107, 113: 106, 114: 105, 115: 104, 116: 103, 117: 102, 118: 101, 119: 100, 120: 99, 121: 98, 122: 97}

s = 'apple PIE'
print(s.translate(reverse_map))
# zkkov PIE
print(s.translate(trans))
# zkkov PIE