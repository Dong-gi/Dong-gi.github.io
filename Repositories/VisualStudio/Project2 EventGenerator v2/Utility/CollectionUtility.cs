using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

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
    }
}
