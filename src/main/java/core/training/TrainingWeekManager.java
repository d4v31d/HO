package core.training;

import core.db.DBManager;
import core.db.JDBCAdapter;
import core.model.HOVerwaltung;
import core.model.enums.DBDataSource;
import core.util.HTDatetime;
import core.util.HOLogger;
import java.sql.ResultSet;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;


/**
 * Class that create a list of TrainingPerWeek objects between 2 dates
 *
 */
public class TrainingWeekManager {

	// cl_NextTrainingDate is seen not as of now but as of LastUpdateDate
	private static Instant cl_NextTrainingDate;
	private static Instant cl_LastUpdateDate;
    private List<TrainingPerWeek> m_Trainings;
    private Instant m_StartDate;
	private Boolean m_IncludeUpcomingTrainings;
	private Boolean m_IncludeMatches;

	/**
	 * Construct a list of TrainingPerWeek since provided initial training Date
	 * @param startDate initial training Date
	 * @param includeUpcomingTrainings whether or not the TrainingPerWeek objects will contain upcoming match information
	 * @param includeMatches whether or not the TrainingPerWeek objects will contain match information
	 */
	public TrainingWeekManager(Instant startDate, boolean includeUpcomingTrainings, boolean includeMatches) {
		if(HOVerwaltung.instance().getModel() == null) {
		HOLogger.instance().error(this.getClass(), "model not yet initialized");
		}
		else{
			if (cl_NextTrainingDate == null) {
					cl_NextTrainingDate = HOVerwaltung.instance().getModel().getXtraDaten().getNextTrainingDate().toInstant();
				    cl_LastUpdateDate = DBManager.instance().getMaxHrf().getDatum().toInstant();
				}
			}

		m_StartDate = startDate;
		m_IncludeMatches = includeMatches;
		m_IncludeUpcomingTrainings = includeUpcomingTrainings;
		m_Trainings = createTrainingListFromHRF();
	}

	/**
	 * Construct a list of TrainingPerWeek of requested size
	 * @param minimumNbEntries requested minimum vector size
	 * @param includeUpcomingTrainings whether or not the TrainingPerWeek objects will contain upcoming match information
	 * @param includeMatches whether or not the TrainingPerWeek objects will contain match information
	 */
	public TrainingWeekManager(int minimumNbEntries, boolean includeUpcomingTrainings, boolean includeMatches) {
		m_StartDate = findStartDate(minimumNbEntries);
		m_IncludeMatches = includeMatches;
		m_IncludeUpcomingTrainings = includeUpcomingTrainings;
		m_Trainings = createTrainingListFromHRF();
	}

	/**
	 * Determine first training date from requested number of entries
	 * @param minimumNbEntries requested size
	 * @return
	 */
	private Instant findStartDate(int minimumNbEntries){
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.from(ZoneOffset.UTC));
			String startDate = formatter.format(Instant.now().minus(365, ChronoUnit.DAYS));
			String sql = String.format("""
					SELECT TRAININGDATE	FROM XTRADATA WHERE XTRADATA.TRAININGDATE >= '%s' 
					ORDER BY TRAININGDATE DESC LIMIT %s""",startDate, minimumNbEntries);

			Instant trainingDate = null;

			try {

				final JDBCAdapter ijdbca = DBManager.instance().getAdapter();
				final ResultSet rs = ijdbca.executeQuery(sql);
				rs.beforeFirst();

				while (rs.next()) {
					trainingDate = rs.getTimestamp("TRAINING_DATE").toInstant();
				}
			}
			catch (Exception e) {
				HOLogger.instance().error(this.getClass(), "Error while performing findDefaultStartDate():  " + e);
			}

			return trainingDate;
	}


	public static TrainingPerWeek getNextWeekTraining(Boolean includeMatches){

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.from(ZoneOffset.UTC));
		String refDate = formatter.format(cl_NextTrainingDate);
		String sql = String.format("""
					SELECT TRAININGDATE, TRAININGSART, TRAININGSINTENSITAET, STAMINATRAININGPART, COTRAINER, TRAINER
					FROM XTRADATA
					INNER JOIN TEAM on XTRADATA.HRF_ID = TEAM.HRF_ID
					INNER JOIN VEREIN on XTRADATA.HRF_ID = VEREIN.HRF_ID
					INNER JOIN SPIELER on XTRADATA.HRF_ID = SPIELER.HRF_ID AND SPIELER.TRAINER > 0
					INNER JOIN (
					     SELECT TRAININGDATE, max(HRF_ID) MAX_HR_ID FROM XTRADATA GROUP BY TRAININGDATE
					) IJ1 ON XTRADATA.HRF_ID = IJ1.MAX_HR_ID
					WHERE XTRADATA.TRAININGDATE > '%s'""", refDate);


		int trainType, trainIntensity, trainStaminaPart, coachLevel, trainingAssistantLevel;
		Instant trainingDate;

		try {

			final JDBCAdapter ijdbca = DBManager.instance().getAdapter();
			final ResultSet rs = ijdbca.executeQuery(sql);
			if(! rs.last()){
				HOLogger.instance().error(TrainingWeekManager.class, "Error while performing getNextWeekTraining()");
				return null;
			}

			int numRows = rs.getRow();

			if(numRows != 1){
				HOLogger.instance().error(TrainingWeekManager.class, "Error while performing getNextWeekTraining()");
				return null;
			}

			rs.beforeFirst();

			while (rs.next()) {
				trainType = rs.getInt("TRAININGSART");
				trainIntensity = rs.getInt("TRAININGSINTENSITAET");
				trainStaminaPart = rs.getInt("STAMINATRAININGPART");
				trainingDate = rs.getTimestamp("TRAININGDATE").toInstant();
				coachLevel = rs.getInt("TRAINER");
				trainingAssistantLevel = rs.getInt("COTRAINER");
				return new TrainingPerWeek(trainingDate, trainType, trainIntensity, trainStaminaPart, trainingAssistantLevel,
						coachLevel, includeMatches, DBDataSource.HRF);
			}
		}
		catch (Exception e) {
			HOLogger.instance().error(TrainingWeekManager.class, "Error while performing getNextWeekTraining():  " + e);
		}

		return null;
	}

	/**
	 * Create the list of trainings from DB but excluding 'Trainings' table
	 *  missing weeks are created by duplicating previous entry
	 */
	private List<TrainingPerWeek> createTrainingListFromHRF(){

		List<TrainingPerWeek>  trainings = new ArrayList<>();

		HashMap<Instant, TrainingPerWeek>  trainingsInDB = createTPWfromDBentries();
		int trainingsSize;

		if (m_StartDate.isAfter(cl_NextTrainingDate)){
			HOLogger.instance().error(this.getClass(), "It was assumed that start date will always be before next training date in database");
			return trainings;
		}

		long nbWeeks = ChronoUnit.DAYS.between(m_StartDate, cl_NextTrainingDate) / 7;


		HTDatetime dtiTrainingDate = new HTDatetime(cl_NextTrainingDate);

		ZonedDateTime zdtCurrDate =  dtiTrainingDate.getHattrickTime().minus(nbWeeks * 7, ChronoUnit.DAYS);

		Instant currDate = zdtCurrDate.toInstant();

		while((currDate.isBefore(cl_NextTrainingDate) || currDate.equals(cl_NextTrainingDate))){

			if ((! m_IncludeUpcomingTrainings) && (HTDatetime.isAfterLastUpdate(zdtCurrDate))){
				break;
			}

			if (trainingsInDB.containsKey(currDate)){
				trainings.add(trainingsInDB.get(currDate));
			}
			else{
				trainingsSize = trainings.size();
				if(trainingsSize != 0)
				{
					var previousTraining = trainings.get(trainingsSize - 1);
					var tpw = new TrainingPerWeek(currDate, previousTraining.getTrainingType(), previousTraining.getTrainingIntensity(), previousTraining.getStaminaShare(), previousTraining.getTrainingAssistantsLevel(), previousTraining.getCoachLevel(),
							m_IncludeMatches, DBDataSource.GUESS);
					trainings.add(tpw);
				}
				else{
					var tpw = new TrainingPerWeek(currDate, -1, 0, 0, 0, 0,
							m_IncludeMatches, DBDataSource.GUESS);
					trainings.add(tpw);
				}
			}

			zdtCurrDate = zdtCurrDate.plus(7, ChronoUnit.DAYS);
			currDate = zdtCurrDate.toInstant();
		}

		return trainings;

	}


	/**
	 * Fetch trainings information from database (excl. Training table)
	 * This excludes manual entries set per user
	 */
	private HashMap<Instant, TrainingPerWeek> createTPWfromDBentries() {

		HashMap<Instant, TrainingPerWeek> output = new HashMap<>();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.from(ZoneOffset.UTC));
		String startDate = formatter.format(m_StartDate);
		String sql = String.format("""
					SELECT TRAININGDATE, TRAININGSART, TRAININGSINTENSITAET, STAMINATRAININGPART, COTRAINER, TRAINER
					FROM XTRADATA
					INNER JOIN TEAM on XTRADATA.HRF_ID = TEAM.HRF_ID
					INNER JOIN VEREIN on XTRADATA.HRF_ID = VEREIN.HRF_ID
					INNER JOIN SPIELER on XTRADATA.HRF_ID = SPIELER.HRF_ID AND SPIELER.TRAINER > 0
					INNER JOIN (
					     SELECT TRAININGDATE, max(HRF_ID) MAX_HR_ID FROM XTRADATA GROUP BY TRAININGDATE
					) IJ1 ON XTRADATA.HRF_ID = IJ1.MAX_HR_ID
					WHERE XTRADATA.TRAININGDATE >= '%s'""",startDate);


		int trainType, trainIntensity, trainStaminaPart, coachLevel, trainingAssistantLevel;
		Instant trainingDate;

		try {

			final JDBCAdapter ijdbca = DBManager.instance().getAdapter();
			final ResultSet rs = ijdbca.executeQuery(sql);
			rs.beforeFirst();

			while (rs.next()) {
				trainType = rs.getInt("TRAININGSART");
				trainIntensity = rs.getInt("TRAININGSINTENSITAET");
				trainStaminaPart = rs.getInt("STAMINATRAININGPART");
				trainingDate = rs.getTimestamp("TRAININGDATE").toInstant();
				coachLevel = rs.getInt("TRAINER");
				trainingAssistantLevel = rs.getInt("COTRAINER");
				TrainingPerWeek tpw = new TrainingPerWeek(trainingDate, trainType, trainIntensity, trainStaminaPart, trainingAssistantLevel,
						coachLevel, m_IncludeMatches, DBDataSource.HRF);
				output.put(trainingDate, tpw);
			}
		}
		catch (Exception e) {
			HOLogger.instance().error(this.getClass(), "Error while performing fetchTrainingListFromDB():  " + e);
		}

		return output;
	}

    public List<TrainingPerWeek> getTrainingList() {
    	return m_Trainings;
    }


	public static Instant getLastUpdateDate() {
		return cl_LastUpdateDate;
	}

	/**
	 * The function push elements of m_Trainings into Training table but not replacing existing entries
	 *
	 */
	public void push2TrainingsTable(){
		DBManager.instance().saveTrainings(m_Trainings, false);
	}

}
