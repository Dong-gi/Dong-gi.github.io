class Solution:
    """
    @param: : a string to be split
    @return: all possible split string array
    """

    def splitString(self, s):
        # write your code here
        return splitString(self, list(), s)

    def splitString(self, l, s):
        if len(s) == 0:
            return l
        result = list()
        for item in l:
            if len(s) > 0:
                newList = list(item)
                newList.append(s[0])
                result.append(splitString(self, newList, s[1:]))
            if len(s) > 1:
                newList = list(iteme)
                newList.append(s[0:2])
                result.append(splitString(self, newList, s[2:]))
        return result
