package com.defiancecraft.configure.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;

/**
 * A class to perform a setup and ask the user questions through
 * stdin, storing the answers in a map.
 */
public class Asker {

	private Map<String, Question> questions = new LinkedHashMap<String, Question>();
	
	/**
	 * Constructs an Asker object; questions are
	 * pairs of three Strings which are:
	 *     name, description, default
	 *     
	 * e.g. new Asker(
	 *     "iceCream", "Favourite flavour of ice cream", "chocolate",
	 *     "colour", "Favourite colour", "green"
	 * );
	 *     
	 * @param questions List of question triplets
	 */
	public Asker(String... questions) {
		
		if (questions.length % 3 != 0)
			throw new IllegalArgumentException("Questions must be in pairs of three.");
		
		for (int i = 0; i < questions.length; i += 3)
			this.questions.put(questions[i], new Question(questions[i + 1], questions[i + 2]));
		
	}
	
	/**
	 * Gets user input for all of the questions passed
	 * when the Asker was created and stores the results
	 * in the array of questions. Answers to the questions
	 * can be retrieved with {@link #getAnswer(String)}
	 */
	public void askQuestions() {
		
		Scanner scanner = new Scanner(System.in);
		
		for (Entry<String, Question> question : this.questions.entrySet()) 
			question.getValue().ask(scanner);
	
	}
	
	/**
	 * Gets an answer to the question given by `name`
	 * 
	 * @param name Name of answer
	 * @return The answer, or null if the question is non-existent.
	 */
	public String getAnswer(String name) {
		return this.questions.containsKey(name) ? this.questions.get(name).getAnswer() : null;
	}
	
	/**
	 * Prints a summary of the user's selected options
	 * and asks whether they are correct. Returns the
	 * result of this question.
	 * 
	 * @return Whether the user confirmed the answers.
	 */
	public boolean confirm() {
		
		// Get max length of description to
		// align them
		int maxLength = questions.values()
				.stream()
				.mapToInt((q) -> { return q.getDescription().length(); })
				.max()
				.getAsInt();
		
		// Print summary of selected options
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("%nSummary:%n"));
		
		for (Question q : questions.values()) {
			sb.append("    ");
			sb.append(q.getDescription());
			sb.append(q.getDescription().length() >= maxLength ? // Fill with spaces of length difference, or nothing if len >= maxLen
					"" : new String(new char[maxLength - q.getDescription().length()]).replace('\0', ' '));
			sb.append(": ");
			sb.append(q.getAnswer());
			sb.append(String.format("%n"));
		}
		
		sb.append(String.format("%nIs this information correct?"));
		sb.append(String.format("%n"));
		sb.append("> [Y]/N ");
		
		System.out.print(sb.toString());
		
		// System.in is closed automatically by the
		// system; no need to close scanner (messes up
		// System.in for next Scanner)
		@SuppressWarnings("resource")
		Scanner scanner   = new Scanner(System.in);
		
		// Wait for newLine
		while (!scanner.hasNextLine());
		
		String line       = scanner.nextLine();
		boolean confirmed = !line.equalsIgnoreCase("n"); // No need to check if blank; blank == "Y" (default)
		
		// Gotta close dat Scanner, so use temporary variable
		return confirmed;
		
	}
	
	/**
	 * A class which holds the description
	 * and default answer to a question. If
	 * the ask() method is called, an answer
	 * is stored and can be retrieved with
	 * getAnswer()
	 */
	public class Question {
		
		private String description;
		private String def;
		private String answer = "";
		
		/**
		 * Constructs a question object with the 
		 * given description (text printed when question
		 * is asked) and default value (if user enters
		 * nothing).
		 * 
		 * @param description Text to print when asking the user
		 * 				      (will be formatted as "%s: [%s]"
		 * 				      with arguments `description` and
		 * 					  `def`)
		 * @param def Default value to use if user does not give
		 * 			  any input.
		 */
		public Question(String description, String def) {
			this.description = description;
			this.def = def;
		}

		public String getDescription() {
			return description;
		}

		public String getDef() {
			return def;
		}
		
		/**
		 * Gets the given answer to the question, returning
		 * the default value if the answer is empty.
		 * 
		 * @return The answer given by the user.
		 */
		public String getAnswer() {
			return answer.isEmpty() ? def : answer;
		}

		/**
		 * Creates a Scanner instance on System.in
		 * before asking the question
		 * 
		 * @see Question#ask(Scanner)
		 */
		public void ask() {
			Scanner s = new Scanner(System.in);
			ask(s);
		}
		
		/**
		 * Asks the user a question and
		 * retrieves their input using given
		 * Scanner.
		 * 
		 * @param s The Scanner to use
		 */
		public void ask(Scanner s) {
			
			System.out.printf("%s: [%s] ", this.description, this.def);
			
			this.answer = s.nextLine();
			if (this.answer.isEmpty())
				this.answer = this.def;
			
		}
		
	}
	
}
