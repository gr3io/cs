package defaultPackage;

import schedule.Generator;

import java.io.IOException;

public class Main {
	public static void main(final String[] argv)  {
		// Zapis logów
		GeneralLogger log = new GeneralLogger();
		try {
			log.setup();
		} catch (SecurityException | IOException e) {
			e.printStackTrace(System.out);
		}

		new Generator();

		log.close();
	}
}

    // https://google.github.io/styleguide/javaguide.html