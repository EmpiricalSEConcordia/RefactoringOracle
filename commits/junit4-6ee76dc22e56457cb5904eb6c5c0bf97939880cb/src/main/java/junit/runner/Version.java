package junit.runner;

/**
 * This class defines the current version of JUnit
 */
public class Version {
	private Version() {
		// don't instantiate
	}

	public static String id() {
		return "4.9-RC1";
	}
	
	public static void main(String[] args) {
		System.out.println(id());
	}
}
