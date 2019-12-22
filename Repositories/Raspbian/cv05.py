import numpy as np
import cv2

def drawCircle(event, x, y, flags, param):
    # print(event, x, y)
    if event == cv2.EVENT_LBUTTONDOWN:
        cv2.circle(img, (x, y), 30, (255, 255, 255), 0)

img = np.zeros((480, 640, 3), np.uint8)
cv2.namedWindow('image')
cv2.setMouseCallback('image', drawCircle)

while cv2.waitKey(10) < 0:
    cv2.imshow('image', img)

cv2.destroyAllWindows()
