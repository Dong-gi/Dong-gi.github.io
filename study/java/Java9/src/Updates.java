public class Updates {

	private static class Resource implements AutoCloseable {
		private String name;
		
		public Resource(String name) {
			this.name = name;
		}

		@Override
		public void close() throws Exception {
			System.out.println(name + " closed");
		}
	}

	private static abstract class Anony<T> {
		private T data;
		public Anony(T data) { this.data = data; }
		public void print() { System.out.println(data.getClass()); }
	}
	
	private static interface I {
		//private String no(); Needs a body
		private String no() {
			return "NO";
		}
		public default void share() {
			System.out.println("Interface's nonabstract methods share private methods : " + no());
		}
	}
	
	public static void main(String[] args) {
		// A final resource
        final Resource resource1 = new Resource("resource1");
        // An effectively final resource
        Resource resource2 = new Resource("resource2");
        try (resource1; resource2) {}
        catch(Exception e) {}
    
        Anony<?> anony = new Anony<>(new Thread()) {};
        anony.print();
        new Anony<>(2) {}.print();
        
        new I() {}.share();
	}

}
