using System;
using System.Collections.Generic;
using System.Linq;

namespace EventGenerator.Utility
{
    static class CollectionUtility
    {
        public static T[,] ToMatrix<T>(List<List<T>> source) => ToMatrix(source.ToArray());
        public static T[,] ToMatrix<T>(List<T>[] source) => ToMatrix(source.Select(x => x.ToArray()).ToArray());
        public static T[,] ToMatrix<T>(List<T[]> source) => ToMatrix(source.ToArray());

        public static T[,] ToMatrix<T>(T[][] source)
        {
            try
            {
                var dim1 = source.Length;
                var dim2 = source.Select(x => x.Length).Max();

                var result = new T[dim1, dim2];
                for (var i = 0; i < dim1; ++i)
                    for (var j = 0; j < dim2; ++j)
                        try
                        {
                            result[i, j] = source[i][j];
                        }
                        catch (Exception) { }
                return result;
            }
            catch (Exception)
            {
                throw;
            }
        }

        public static IEnumerable<T> Concat<T>(params IEnumerable<T>[] enumerables) => enumerables.SelectMany(x => x);
        public static IEnumerable<T> Concat<T>(this IEnumerable<T> self, params IEnumerable<T>[] enumerables)
        {
            var merge = new IEnumerable<T>[enumerables.Length + 1];
            merge[0] = self;
            Array.Copy(enumerables, 0, merge, 1, enumerables.Length);
            return Concat(merge);
        }
        public static IEnumerable<T> With<T>(this IEnumerable<T> self, Type type)
            => self.Where(x => type.IsInstanceOfType(x));
        public static IEnumerable<T> With<T, AS>(this IEnumerable<T> self)
            => self.Where(x => x is AS);
        public static IEnumerable<AS> WithAs<T, AS>(this IEnumerable<T> self) where AS : class
            => self.Where(x => x is AS).Select(x => x as AS);
    }
}
