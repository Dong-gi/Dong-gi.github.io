import numpy as np
import cv2

# imread(file, mode)
# file should be in the working directory or a full path of image
# if there's no such image, returns None
# mode
#     cv2.IMREAD_COLOR == 1
#     cv2.IMREAD_GRAYSCALE == 0
#     cv2.IMREAD_UNCHANGED == -1
img = cv2.imread('data/butterfly.jpg')
print(img)
cv2.imshow('title', img)
cv2.namedWindow('image', cv2.WINDOW_NORMAL)
cv2.imshow('image', img)
# waitKey(milliseconds) : keyboard binding
print(cv2.waitKey(0))
cv2.destroyWindow('title')
print(cv2.waitKey(0))
cv2.destroyAllWindows()
cv2.imwrite('cv01.png', img)
