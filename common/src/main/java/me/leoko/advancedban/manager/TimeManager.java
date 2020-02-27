package me.leoko.advancedban.manager;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.UtilityClass;
import me.leoko.advancedban.AdvancedBan;
import org.omg.PortableServer.AdapterActivator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@UtilityClass
public class TimeManager {
    public long getTime() {
        return System.currentTimeMillis() + TimeUnit.HOURS.toMillis(AdvancedBan.get().getConfiguration().getTimeDifferential());
    }

    public long toMilliSec(String s) {
        // This is not my regex :P | From: http://stackoverflow.com/a/8270824
        String[] sl = s.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");

        long i = Long.parseLong(sl[0]);

        switch (sl[1]) {
            case "m":
                return TimeUnit.MINUTES.toMillis(i);
            case "h":
                return TimeUnit.HOURS.toMillis(i);
            case "d":
                return TimeUnit.DAYS.toMillis(i);
            case "w":
                return TimeUnit.DAYS.toMillis(i) * 7;
            case "mo":
                return TimeUnit.DAYS.toMillis(i) * 30;
            case "y":
                return TimeUnit.DAYS.toMillis(i) * 365;
            default:
                return TimeUnit.SECONDS.toMillis(i);
        }
    }

    public String getDate(long date) {
        SimpleDateFormat format = new SimpleDateFormat(AdvancedBan.get().getConfiguration().getDateFormat());
        return format.format(new Date(date));
    }
}