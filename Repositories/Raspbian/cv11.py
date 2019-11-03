import numpy as np
import cv2


img = cv2.imread('start05-2.jpg')
kernel = np.ones((5, 5), np.float32) / 25
cv2.imshow('average filter', cv2.filter2D(img, -1, kernel))
# or cv2.boxFilter()
cv2.waitKey(0)
cv2.destroyAllWindows()

cv2.imshow('blur', cv2.blur(img, (5, 5)))
cv2.waitKey(0)
cv2.destroyAllWindows()

cv2.imshow('gaussian blur', cv2.GaussianBlur(img, (5, 5), 0))
# cv2.getGaussianKernel()
cv2.waitKey(0)
cv2.destroyAllWindows()

cv2.imshow('median blur', cv2.medianBlur(img, 5))
cv2.waitKey(0)
cv2.destroyAllWindows()

cv2.imshow('bilateral', cv2.bilateralFilter(img, 9, 75, 75))
cv2.waitKey(0)
cv2.destroyAllWindows()
