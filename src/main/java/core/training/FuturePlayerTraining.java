package core.training;

import core.model.HOVerwaltung;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;

public class FuturePlayerTraining {


    public boolean contains(Instant trainingDate) {
        // from<=date & to>date
        if ( !from.isAfter(trainingDate)  ) {
            if  ( to == null ) return true;
            var endOfToWeek = to.toInstant().plus(Duration.ofDays(7));
            return endOfToWeek.isAfter(trainingDate);
        }
        return false;
    }

    public enum Priority {
        NO_TRAINING(0),
        OSMOSIS_TRAINING(1),
        PARTIAL_TRAINING(2),
        FULL_TRAINING(3);

        private int value;
        private static HashMap<Integer, Priority> map = new HashMap<>();

        Priority(int value) {
            this.value = value;
        }

        static {
            for (Priority p : Priority.values()) {
                map.put(p.value, p);
            }
        }

        public static Priority valueOf(int p) {
            return map.get(p);
        }

        public int getValue() {
            return value;
        }

        public String toString(){
            return switch (value) {
                case 3 -> HOVerwaltung.instance().getLanguageString("trainpre.fulltrain");
                case 2 -> HOVerwaltung.instance().getLanguageString("trainpre.partialtrain");
                case 1 -> HOVerwaltung.instance().getLanguageString("trainpre.osmosistrain");
                case 0 -> HOVerwaltung.instance().getLanguageString("trainpre.notrain");
                default -> "";
            };
        }
    }

    /**
     * Player Id
     */
    private int playerId;
    /**
     * first week of training interval
     */
    private HattrickDate from;
    /**
     * last week of training interval (null if training is planned forever)
     */
    private HattrickDate to;
    /**
     * priority of the training (overrides automatic determination by best position)
     */
    private Priority priority;

    public FuturePlayerTraining(int playerId, FuturePlayerTraining.Priority prio, HattrickDate from, HattrickDate to) {
        this.playerId = playerId;
        this.priority = prio;
        this.from = from;
        this.to = to;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority prio) {
        this.priority = prio;
    }

    public int getPlayerId() {
        return this.playerId;
    }

    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    public HattrickDate getFrom() {
        return from;
    }

    public void setFrom(HattrickDate from) {
        this.from = from;
    }

    public HattrickDate getTo() {
        return to;
    }

    public void setTo(HattrickDate to) {
        this.to = to;
    }

    /**
     * check if week is during the planned training interval
     * @param week week to test
     * @return true if week is during current interval
     */
    public boolean isInWeek(HattrickDate week) {
        return week.isBetween(this.from, this.to);
    }

    /**
     * Cut the given time interval from the current training interval
     *
     * @param from HattrickDate
     * @param to   HattrickDate
     *
     * @return false if remaining training interval is not empty
     *          true if training is completely replaced by the new interval
     */
    public boolean cut(Instant from, Instant to) {
        if (from.isAfter(this.to.toInstant()) || this.from.isAfter(to)) {
            // this is outside the given interval
            return false;
        }

        if (from.isAfter(this.from.toInstant())) {
            this.to = HattrickDate.getHattrickDateByDate(from);
            this.to.addWeeks(-1);
            return false;
        }
        if ( to != null && (this.to == null || this.to.isAfter(to))) {
            this.from = HattrickDate.getHattrickDateByDate(to);
            this.from.addWeeks(1);
            return false;
        }
        return true; // completely replaced
    }

}