
public class Notify {

	private static final Object LOCK = new Object(); 

	public static void main(String[] args) { 
		Thread th1 = new Thread(()->{ 
			System.out.println("Thread 1"); 
			try { 
				synchronized(LOCK) { 
					System.out.println("Thread 1 sleep"); 
					LOCK.wait(); 
				} 
				System.out.println("Thread 1 waked up"); 
				System.out.println("Thread 1 notify Thread 2"); 
				synchronized(LOCK) { 
					LOCK.notify(); 
				} 
			} catch (InterruptedException e) { 
				e.printStackTrace(); 
			} 
		});
		Thread th2 = new Thread(() -> { 
			System.out.println("Thread 2"); 
			try { 
				synchronized(LOCK) { 
					System.out.println("Thread 2 notify Thread 1"); 
					LOCK.notify(); 
					System.out.println("Thread 2 sleep"); 
					LOCK.wait(); 
				} 
				System.out.println("Thread 2 waked up"); 
			} catch (InterruptedException e) { 
				e.printStackTrace(); 
			} 
		});
		th1.start(); 
		try { 
			Thread.sleep(1000); 
		} catch (Exception e) {} 
		th2.start(); 
	}

}