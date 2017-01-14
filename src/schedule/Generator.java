package schedule;

import defaultPackage.Hour;

import java.util.ArrayList;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import io.*;
import resourcesObjects.*;

public class Generator {
	private static final Logger LOG = Logger.getLogger( Logger.GLOBAL_LOGGER_NAME );
	public static final String[] DAYS = {
			"Poniedziałek",
			"Wtorek",
			"Środa",
			"Czwartek",
			"Piątek"
	};

	public static final Hour[] HOURS = {
			new Hour("8:00", "9:30"),
			new Hour("9:45", "11:15"),
			new Hour("11:30", "13:00"),
			new Hour("13:15", "14:45"),
			new Hour("15:00", "16:30"),
			new Hour("16:45", "18:15"),
			new Hour("18:30", "20:00"),
	};

	MySQLReader mySQLParser;
	CSVWriter csvParser;
	ArrayList<CourseObject> courses;
	ArrayList<GroupObject> groups;
	ArrayList<ProfessorObject> professors;
	ArrayList<RoomObject> rooms;
	ArrayList<ClassObject> classes;
	ArrayList<ScheduleObject> scheduleArrayList;

	/**
	 * ScheduleObjectObject (Array of class ID from Database)
	 * 1 - room number
	 * 2 - hour
	 * 3 - day
	 */
	int scheduleArray[][][];

	/**
	 * Number of rooms
	 */
	int n;

	public Generator() {
		courses = new ArrayList<>();
		groups = new ArrayList<>();
		professors = new ArrayList<>();
		rooms = new ArrayList<>();
		classes = new ArrayList<>();

		readResources();

		n = rooms.size();
		// 7 - hours per day
		// 5 - days per week
		scheduleArray = new int[n][7][5];

		readPreferences();
		algorithm();

		try {
			csvParser = new CSVWriter(scheduleArrayList);
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "", e);
		}

		LOG.log(Level.INFO, "Alghoritm finished without errors");
	}

	void readResources() {
		mySQLParser = new MySQLReader();
		mySQLParser.readCourses(courses);
		mySQLParser.readGropus(groups);
		mySQLParser.readProfessors(professors);
		mySQLParser.readRooms(rooms);
		mySQLParser.readClasses(classes);

		for (ClassObject aClass : classes)
			aClass.resolveClassObject(courses, groups, professors);
		LOG.log(Level.INFO, "Finished reading resources");
	}

	void readPreferences() {
		// Read JSON files and write preferences to objects
		new JSONReader(groups, professors, rooms);

		// Assign preferences to schedule
		assignProfessorPreferences();
		assignGroupPreferences();

		LOG.log(Level.INFO, "Finished reading preferences");
	}

	void assignProfessorPreferences() {
		ListIterator<ClassObject> iterator = classes.listIterator();
		int roomIndex;

		classLoop:
		while(iterator.hasNext()) {
			ClassObject classItem = iterator.next();

			int[][] professorPreferences = classItem.getProfessor().getPreferences();

			if (professorPreferences == null)
				continue;

			for(int j = 0; j < 5; j++)
				for (int i = 0; i < 7; i++)
					if ((roomIndex = professorPreferences[i][j]) != -1) {
						if (scheduleArray[roomIndex][i][j] != 0)
							continue;

						boolean conflict = checkConflicts(classItem, i, j);
						if (conflict)
							continue classLoop;

						// assign class to scheduleArrayList and go to next class
						scheduleArray[roomIndex][i][j] = classItem.getId();
						// delete assigned preference from preferences table
						professorPreferences[i][j] = -1;
						continue classLoop;
					}
		}
	}

	void assignGroupPreferences() {
		ListIterator<ClassObject> iterator = classes.listIterator();
		int roomIndex;

		classLoop:
		while (iterator.hasNext()) {
			ClassObject classItem = iterator.next();

			int[][] groupPreferences = classItem.getGroup().getPreferences();

			if (groupPreferences == null)
				continue;

			for (int j = 0; j < 5; j++)
				for (int i = 0; i < 7; i++)
					if ((roomIndex = groupPreferences[i][j]) != -1) {
						if (scheduleArray[roomIndex][i][j] != 0)
							continue;

						boolean conflict = checkConflicts(classItem, i, j);
						if (conflict)
							continue classLoop;

						// assign class to scheduleArrayList and go to next class
						scheduleArray[roomIndex][i][j] = classItem.getId();
						continue classLoop;
					}
		}
	}

	/**
	 * Assigns classes to the 3D resources relay (room, hour and day)
	 */
	void algorithm() {
		// i - room number
		// j - hour
		// k - day
		//int n = rooms.size();	// number of rooms

		// ScheduleObjectObject
		// 7 - hours per day
		// 5 - days per week
		//scheduleArray[][][] = new int[n][7][5];

		ListIterator<ClassObject> iterator = classes.listIterator();

		classLoop:
		while(iterator.hasNext()) {
			ClassObject classItem = iterator.next();

			if (!iterator.hasPrevious())
				scheduleArray[0][0][0] = classItem.getId();

			dayLoop:
			for (int k = 0; k < 5; k++) {
				hourLoop:
				for (int j = 0; j < 7; j++) {
					roomLoop:
					for (int i = 0; i < n; i++) {

						if (scheduleArray[i][j][k] != 0)
							continue;

						boolean conflict = checkConflicts(classItem, j, k);
						if (conflict)
							continue hourLoop;

						// assign class to scheduleArrayList and go to next class
						scheduleArray[i][j][k] = classItem.getId();
						continue classLoop;
					}
				}
			}
			//LOG.log(Level.WARNING, "Nie dodano zajęcia do planu", classItem);
		}
		LOG.log(Level.INFO, "Assigned classes to the schedule");
		generate(scheduleArray);
	}

	/**
	 * Generates scheduleArrayList
	 * Resolves keys in scheduleArray, finds their objects and puts in
	 * to ScheduleObjectObject Array List
	 * @param scheduleArray is array of 3D dependency created in alghoritm.
	 */
	void generate(int scheduleArray[][][]) {
		int idClass, roomNumber, hourNumber, dayNumber;
		ListIterator<ClassObject> iterator;
		ClassObject classItem = null;
		scheduleArrayList = new ArrayList<>();

		dayLoop:
		for (int k = 0; k < 5; k++) {
			hourLoop:
			for (int j = 0; j < 7; j++) {
				roomLoop:
				for (int i = 0; (i < rooms.size()) && (scheduleArray[i][j][k] != 0); i++) {
					idClass = scheduleArray[i][j][k];
					roomNumber = i;
					hourNumber = j;
					dayNumber = k;

					String day = DAYS[dayNumber];
					Hour hour = HOURS[hourNumber];
					RoomObject room = rooms.get(roomNumber);

					iterator = classes.listIterator();

					while (iterator.hasNext()) {
						classItem = iterator.next();
						if (classItem.getId() == idClass)
							break;
					}

					try {
						scheduleArrayList.add( new ScheduleObject (classItem, room, hour, day));
						LOG.log(Level.INFO, "Added class to scheduleArrayList");
					} catch (Exception e) {
						LOG.log(Level.WARNING, "Couldn't add class to scheduleArrayList", e);
					}
				}
			}
		}
	}

	/**
	 * Checks if Professor or Group has classes in other room at the same time
	 * @param classItem is class object to check
	 * @param hour is an hour when we check conflicts
	 * @param day is a day when method checks for conflicts
	 * @return returns true if conflict exists or false if not
	 */
	Boolean checkConflicts(ClassObject classItem, int hour, int day) {
		ClassObject controlClass = null;

		// for efery room at the same time:
		for (int it = 0; (it < n) &&
				(scheduleArray[it][hour][day] != 0); it++) {
			// get control class id form schedule
			int controlClassId = scheduleArray[it][hour][day];

			// find control class
			for (ClassObject classObject : classes)
				if(classObject.getId() == controlClassId) {
					controlClass = classObject;
					break;
				}

			// check if professor or group of control class is already assigned
			assert controlClass != null;
			if ((controlClass.getProfessorId() == classItem.getProfessorId())
					|| (controlClass.getGroupId() == classItem.getGroupId()))
				return true;
		}
		return false;
	}
}
