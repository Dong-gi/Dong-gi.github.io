import numpy as np
import cv2


img = cv2.imread('start05-2.jpg')
cv2.imshow('laplacian', cv2.Laplacian(img, cv2.CV_64F))
cv2.waitKey(0)
cv2.destroyAllWindows()

sobelx = cv2.Sobel(img, cv2.CV_64F, 1, 0, ksize=5)
sobely = cv2.Sobel(img, cv2.CV_64F, 0, 1, ksize=5)
#sobelx = np.uint8(np.absolute(cv2.Sobel(img, cv2.CV_64F, 1, 0, ksize=5)))
#sobely = np.uint8(np.absolute(cv2.Sobel(img, cv2.CV_64F, 0, 1, ksize=5)))
#cv2.imshow('sobel x', sobelx)
#cv2.imshow('sobel y', sobely)
cv2.imshow('sobel', cv2.Sobel(img, cv2.CV_64F, 1, 1, ksize=5))
cv2.imshow('sobel 2', cv2.addWeighted(sobelx, 0.5, sobely, 0.5, 0))
cv2.waitKey(0)
cv2.destroyAllWindows()

# Canny(src, low_thresh, high_thresh)
cv2.imshow('canny', cv2.Canny(img, 100, 200))
cv2.waitKey(0)
cv2.destroyAllWindows()
