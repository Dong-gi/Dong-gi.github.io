m = memoryview(b'ABCD')
print(m.cast('B').tolist()) # [65, 66, 67, 68]
print(m.cast('b').tolist()) # [65, 66, 67, 68]
print(m.cast('c').tolist()) # [b'A', b'B', b'C', b'D']

m = memoryview(bytes.fromhex('00112233445566778899AABBCCDDEEFF'))
print(m.cast('B').tolist()) # [0, 17, 34, 51, 68, 85, 102, 119, 136, 153, 170, 187, 204, 221, 238, 255]
print(m.cast('b').tolist()) # [0, 17, 34, 51, 68, 85, 102, 119, -120, -103, -86, -69, -52, -35, -18, -1]
print(m.cast('c').tolist()) # [b'\x00', b'\x11', b'"', b'3', b'D', b'U', b'f', b'w', b'\x88', b'\x99', b'\xaa', b'\xbb', b'\xcc', b'\xdd', b'\xee', b'\xff']

print(m.cast('B', shape=[4, 4]).tolist())
# [[0, 17, 34, 51], [68, 85, 102, 119], [136, 153, 170, 187], [204, 221, 238, 255]]
print(m.cast('B', shape=[2, 4, 2]).tolist())
# [[[0, 17], [34, 51], [68, 85], [102, 119]], [[136, 153], [170, 187], [204, 221], [238, 255]]]