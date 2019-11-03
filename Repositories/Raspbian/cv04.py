import numpy as np
import cv2

img = np.zeros((480, 640, 3), np.uint8)

img = cv2.line(img, (0, 0), (360, 240), (255, 0, 0), 4)

img = cv2.rectangle(img, (100, 100), (200, 200), (0, 255, 0), 3)

img = cv2.circle(img, (360, 240), 50, (0, 0, 255), -1)

# center, lengths, rotationAngle, startAngle, endAngle
img = cv2.ellipse(img, (400, 400), (100, 50), 0, 0, 180, 255, -1)

pts = np.array([[600, 400], [500, 400], [500, 300], [400, 200]], np.int32)
print(pts)
pts = pts.reshape((-1, 1, 2))
print(pts)
img = cv2.polylines(img, [pts], True, (0, 255, 255))

font = cv2.FONT_HERSHEY_SIMPLEX
# text, startPoint, font, scale, color, thickness, lineType
cv2.putText(img, 'OpenCV', (200, 300), font, 2, (255, 255, 255), 2, cv2.LINE_AA)

cv2.imshow('drawing', img)
cv2.waitKey(0)
