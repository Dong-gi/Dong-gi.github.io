m_original = memoryview(b'ABCD')
m_transformed = m_original.cast('B', shape=[2,2])

print(m_transformed.tolist())       # [[65, 66], [67, 68]]
print(m_transformed.tobytes())      # b'ABCD'
print(m_transformed.tobytes('C'))   # b'ABCD'
print(m_transformed.tobytes('F'))   # b'ACBD'
print(m_transformed.tobytes('A'))   # b'ABCD'