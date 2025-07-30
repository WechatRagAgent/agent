package com.wechat.rag.core.agent.query;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自然语言时间解析器
 * 将自然语言时间表达转换为时间戳范围
 */
@Component
@Slf4j
public class TimeParser {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // 基础时间表达式模式
    private static final Pattern TODAY_PATTERN = Pattern.compile("今天|今日");
    private static final Pattern YESTERDAY_PATTERN = Pattern.compile("昨天|昨日");
    private static final Pattern DAY_BEFORE_YESTERDAY_PATTERN = Pattern.compile("前天|前日");
    private static final Pattern THIS_WEEK_PATTERN = Pattern.compile("本周|这周");
    private static final Pattern LAST_WEEK_PATTERN = Pattern.compile("上周|上星期|上个星期");

    // 时间段模式
    private static final Pattern MORNING_PATTERN = Pattern.compile("上午|早上|早晨");
    private static final Pattern AFTERNOON_PATTERN = Pattern.compile("下午");
    private static final Pattern EVENING_PATTERN = Pattern.compile("晚上|傍晚");
    private static final Pattern NIGHT_PATTERN = Pattern.compile("深夜|夜里|凌晨");

    // 最近几天的模式 - 匹配"最近3天"、"近期2天"等
    private static final Pattern RECENT_DAYS_PATTERN = Pattern.compile("最近(\\d+)天|近期(\\d+)天|过去(\\d+)天|前(\\d+)天");

    // 最近几周的模式
    private static final Pattern RECENT_WEEKS_PATTERN = Pattern.compile("最近(\\d+)周|近期(\\d+)周|过去(\\d+)周|前(\\d+)周|最近(\\d+)个星期|近期(\\d+)个星期");

    // 最近几个月的模式
    private static final Pattern RECENT_MONTHS_PATTERN = Pattern.compile("最近(\\d+)个月|近期(\\d+)个月|过去(\\d+)个月|前(\\d+)个月");

    // 月份范围模式
    private static final Pattern THIS_MONTH_PATTERN = Pattern.compile("本月|这个月|当月");
    private static final Pattern LAST_MONTH_PATTERN = Pattern.compile("上月|上个月|上一个月");

    // 一般性的"最近"模式（不带具体数字）
    private static final Pattern RECENT_GENERAL_PATTERN = Pattern.compile("最近|近期|近日|这几天|这段时间");

    // 具体日期模式 (yyyy-MM-dd, MM-dd, dd等)
    private static final Pattern SPECIFIC_DATE_PATTERN = Pattern.compile("(\\d{4})[-/年](\\d{1,2})[-/月](\\d{1,2})[日号]?|(\\d{1,2})[-/月](\\d{1,2})[日号]?|(\\d{1,2})[日号]");

    /**
     * 解析时间表达式
     * @param timeExpression 时间表达式，如"昨天下午"、"最近3天"、"近期"等
     * @return 时间范围对象，包含开始和结束时间戳
     */
    public TimeRange parseTimeExpression(String timeExpression) {
        if (timeExpression == null || timeExpression.trim().isEmpty()) {
            return null;
        }

        timeExpression = timeExpression.trim();
        log.debug("解析时间表达式: {}", timeExpression);

        // 优先处理周范围表达式
        if (THIS_WEEK_PATTERN.matcher(timeExpression).find() || LAST_WEEK_PATTERN.matcher(timeExpression).find()) {
            return parseWeekRange(timeExpression);
        }

        // 处理月范围表达式
        if (THIS_MONTH_PATTERN.matcher(timeExpression).find() || LAST_MONTH_PATTERN.matcher(timeExpression).find()) {
            return parseMonthRange(timeExpression);
        }

        // 处理"最近"相关的表达
        TimeRange recentRange = parseRecentExpression(timeExpression);
        if (recentRange != null) {
            return recentRange;
        }

        // 处理传统的时间表达（向下兼容）
        LocalDate baseDate = getBaseDate(timeExpression);
        TimeOfDay timeOfDay = getTimeOfDay(timeExpression);

        if (baseDate == null) {
            return null;
        }

        return createTimeRange(baseDate, timeOfDay);
    }

    /**
     * *** 新增方法：解析"最近"相关的时间表达 ***
     */
    private TimeRange parseRecentExpression(String timeExpression) {
        LocalDateTime now = LocalDateTime.now();

        // 匹配"最近N天"
        Matcher daysMatcher = RECENT_DAYS_PATTERN.matcher(timeExpression);
        if (daysMatcher.find()) {
            int days = extractNumber(daysMatcher);
            if (days > 0) {
                LocalDateTime startTime = now.minusDays(days).withHour(0).withMinute(0).withSecond(0).withNano(0);
                return createTimeRangeFromDateTime(startTime, now);
            }
        }

        // 匹配"最近N周"
        Matcher weeksMatcher = RECENT_WEEKS_PATTERN.matcher(timeExpression);
        if (weeksMatcher.find()) {
            int weeks = extractNumber(weeksMatcher);
            if (weeks > 0) {
                LocalDateTime startTime = now.minusWeeks(weeks).withHour(0).withMinute(0).withSecond(0).withNano(0);
                return createTimeRangeFromDateTime(startTime, now);
            }
        }

        // 匹配"最近N个月"
        Matcher monthsMatcher = RECENT_MONTHS_PATTERN.matcher(timeExpression);
        if (monthsMatcher.find()) {
            int months = extractNumber(monthsMatcher);
            if (months > 0) {
                LocalDateTime startTime = now.minusMonths(months).withHour(0).withMinute(0).withSecond(0).withNano(0);
                return createTimeRangeFromDateTime(startTime, now);
            }
        }

        // 匹配一般性的"最近"（默认为最近7天）
        if (RECENT_GENERAL_PATTERN.matcher(timeExpression).find()) {
            int defaultDays = getDefaultRecentDays(timeExpression);
            LocalDateTime startTime = now.minusDays(defaultDays).withHour(0).withMinute(0).withSecond(0).withNano(0);
            log.info("识别到一般性'最近'表达，默认使用{}天范围", defaultDays);
            return createTimeRangeFromDateTime(startTime, now);
        }

        return null;
    }

    /**
     * 从正则匹配结果中提取数字
     */
    private int extractNumber(Matcher matcher) {
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String group = matcher.group(i);
            if (group != null && !group.isEmpty()) {
                try {
                    return Integer.parseInt(group);
                } catch (NumberFormatException e) {
                    log.warn("解析数字失败: {}", group);
                }
            }
        }
        return 0;
    }

    /**
     * 根据不同的"最近"表达获取默认天数
     */
    private int getDefaultRecentDays(String timeExpression) {
        if (timeExpression.contains("近日") || timeExpression.contains("这几天")) {
            return 3; // 近日默认3天
        } else if (timeExpression.contains("这段时间")) {
            return 7; // 这段时间默认7天
        } else {
            return 7; // 其他"最近"默认7天
        }
    }

    /**
     * 解析周范围表达式
     */
    private TimeRange parseWeekRange(String timeExpression) {
        LocalDate now = LocalDate.now();
        LocalDate weekStart;
        
        if (THIS_WEEK_PATTERN.matcher(timeExpression).find()) {
            // 本周：从本周一到本周日
            weekStart = now.with(DayOfWeek.MONDAY);
        } else if (LAST_WEEK_PATTERN.matcher(timeExpression).find()) {
            // 上周：从上周一到上周日
            weekStart = now.with(DayOfWeek.MONDAY).minusWeeks(1);
        } else {
            return null;
        }
        
        LocalDate weekEnd = weekStart.plusDays(6); // 周日
        
        // 检查是否有时间段修饰
        TimeOfDay timeOfDay = getTimeOfDay(timeExpression);
        if (timeOfDay != TimeOfDay.ALL_DAY) {
            // 如果有时间段修饰，应用到周的第一天
            return createTimeRange(weekStart, timeOfDay);
        }
        
        // 完整周范围：从周一00:00:00到周日23:59:59
        LocalDateTime startDateTime = weekStart.atStartOfDay();
        LocalDateTime endDateTime = weekEnd.atTime(23, 59, 59);
        
        return createTimeRangeFromDateTime(startDateTime, endDateTime);
    }

    /**
     * 解析月范围表达式
     */
    private TimeRange parseMonthRange(String timeExpression) {
        LocalDate now = LocalDate.now();
        LocalDate monthStart;
        LocalDate monthEnd;
        
        if (THIS_MONTH_PATTERN.matcher(timeExpression).find()) {
            // 本月：从本月1号到本月最后一天
            monthStart = now.withDayOfMonth(1);
            monthEnd = now.withDayOfMonth(now.lengthOfMonth());
        } else if (LAST_MONTH_PATTERN.matcher(timeExpression).find()) {
            // 上月：从上月1号到上月最后一天
            LocalDate lastMonth = now.minusMonths(1);
            monthStart = lastMonth.withDayOfMonth(1);
            monthEnd = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth());
        } else {
            return null;
        }
        
        // 检查是否有时间段修饰
        TimeOfDay timeOfDay = getTimeOfDay(timeExpression);
        if (timeOfDay != TimeOfDay.ALL_DAY) {
            // 如果有时间段修饰，应用到月的第一天
            return createTimeRange(monthStart, timeOfDay);
        }
        
        // 完整月范围：从1号00:00:00到最后一天23:59:59
        LocalDateTime startDateTime = monthStart.atStartOfDay();
        LocalDateTime endDateTime = monthEnd.atTime(23, 59, 59);
        
        return createTimeRangeFromDateTime(startDateTime, endDateTime);
    }

    /**
     * 从LocalDateTime创建时间范围
     */
    private TimeRange createTimeRangeFromDateTime(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        long startTimestamp = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endTimestamp = endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        log.debug("时间范围: {} - {} ({} - {})",
                startDateTime, endDateTime, startTimestamp, endTimestamp);

        return new TimeRange(startTimestamp, endTimestamp);
    }

    /**
     * 获取基准日期（原有方法保持不变）
     */
    private LocalDate getBaseDate(String timeExpression) {
        LocalDate now = LocalDate.now();

        // 检查具体日期
        Matcher specificMatcher = SPECIFIC_DATE_PATTERN.matcher(timeExpression);
        if (specificMatcher.find()) {
            return parseSpecificDate(specificMatcher, now);
        }

        // 检查相对日期
        if (TODAY_PATTERN.matcher(timeExpression).find()) {
            return now;
        }
        if (YESTERDAY_PATTERN.matcher(timeExpression).find()) {
            return now.minusDays(1);
        }
        if (DAY_BEFORE_YESTERDAY_PATTERN.matcher(timeExpression).find()) {
            return now.minusDays(2);
        }
        // 注意：周范围表达式现在由专门的parseWeekRange方法处理
        // 这里保留是为了向下兼容，但在新的调度器逻辑中不会执行到这里

        return null;
    }

    /**
     * 解析具体日期（原有方法保持不变）
     */
    private LocalDate parseSpecificDate(Matcher matcher, LocalDate now) {
        try {
            if (matcher.group(1) != null) {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int day = Integer.parseInt(matcher.group(3));
                return LocalDate.of(year, month, day);
            }
            else if (matcher.group(4) != null) {
                int month = Integer.parseInt(matcher.group(4));
                int day = Integer.parseInt(matcher.group(5));
                return LocalDate.of(now.getYear(), month, day);
            }
            else if (matcher.group(6) != null) {
                int day = Integer.parseInt(matcher.group(6));
                return LocalDate.of(now.getYear(), now.getMonth(), day);
            }
        } catch (Exception e) {
            log.warn("解析具体日期失败: {}", matcher.group(), e);
        }
        return null;
    }

    /**
     * 获取时间段（原有方法保持不变）
     */
    private TimeOfDay getTimeOfDay(String timeExpression) {
        if (MORNING_PATTERN.matcher(timeExpression).find()) {
            return TimeOfDay.MORNING;
        }
        if (AFTERNOON_PATTERN.matcher(timeExpression).find()) {
            return TimeOfDay.AFTERNOON;
        }
        if (EVENING_PATTERN.matcher(timeExpression).find()) {
            return TimeOfDay.EVENING;
        }
        if (NIGHT_PATTERN.matcher(timeExpression).find()) {
            return TimeOfDay.NIGHT;
        }
        return TimeOfDay.ALL_DAY;
    }

    /**
     * 创建时间范围（原有方法保持不变）
     */
    private TimeRange createTimeRange(LocalDate date, TimeOfDay timeOfDay) {
        LocalDateTime startDateTime;
        LocalDateTime endDateTime;

        switch (timeOfDay) {
            case MORNING:
                startDateTime = date.atTime(6, 0);
                endDateTime = date.atTime(12, 0);
                break;
            case AFTERNOON:
                startDateTime = date.atTime(12, 0);
                endDateTime = date.atTime(18, 0);
                break;
            case EVENING:
                startDateTime = date.atTime(18, 0);
                endDateTime = date.atTime(22, 0);
                break;
            case NIGHT:
                startDateTime = date.atTime(22, 0);
                endDateTime = date.plusDays(1).atTime(6, 0);
                break;
            default:
                startDateTime = date.atStartOfDay();
                endDateTime = date.atTime(23, 59, 59);
                break;
        }

        long startTimestamp = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endTimestamp = endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        log.debug("时间范围: {} - {} ({} - {})",
                startDateTime, endDateTime, startTimestamp, endTimestamp);

        return new TimeRange(startTimestamp, endTimestamp);
    }

    /**
     * 时间段枚举
     */
    private enum TimeOfDay {
        MORNING,
        AFTERNOON,
        EVENING,
        NIGHT,
        ALL_DAY
    }

    /**
     * 时间范围数据类
     */
    public static class TimeRange {
        private final long startTimestamp;
        private final long endTimestamp;

        public TimeRange(long startTimestamp, long endTimestamp) {
            this.startTimestamp = startTimestamp;
            this.endTimestamp = endTimestamp;
        }

        public long getStartTimestamp() {
            return startTimestamp;
        }

        public long getEndTimestamp() {
            return endTimestamp;
        }

        @Override
        public String toString() {
            return String.format("TimeRange{start=%d, end=%d}", startTimestamp, endTimestamp);
        }
    }
}
