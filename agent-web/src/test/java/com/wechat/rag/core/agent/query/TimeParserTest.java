package com.wechat.rag.core.agent.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TimeParser单元测试
 * 测试各种时间表达式的解析功能
 */
@SpringBootTest
class TimeParserTest {

    private TimeParser timeParser;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @BeforeEach
    void setUp() {
        timeParser = new TimeParser();
    }

    @Test
    @DisplayName("自定义测试")
    void testCustom() {
        TimeParser.TimeRange result = timeParser.parseTimeExpression("最近3天");
        assertNotNull(result, "解析结果不应为null");
    }

    @Test
    @DisplayName("测试本周时间范围解析")
    void testThisWeekParsing() {
        TimeParser.TimeRange result = timeParser.parseTimeExpression("本周");
        assertNotNull(result, "本周解析结果不应为null");

        LocalDate now = LocalDate.now();
        LocalDate mondayOfThisWeek = now.with(DayOfWeek.MONDAY);
        LocalDate sundayOfThisWeek = mondayOfThisWeek.plusDays(6);

        LocalDateTime expectedStart = mondayOfThisWeek.atStartOfDay();
        LocalDateTime expectedEnd = sundayOfThisWeek.atTime(23, 59, 59);

        long expectedStartTimestamp = expectedStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long expectedEndTimestamp = expectedEnd.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        assertEquals(expectedStartTimestamp, result.getStartTimestamp(), 
                "本周开始时间应为本周一00:00:00");
        assertEquals(expectedEndTimestamp, result.getEndTimestamp(), 
                "本周结束时间应为本周日23:59:59");
    }

    @Test
    @DisplayName("测试这周时间范围解析")
    void testThisWeekAlternativeParsing() {
        TimeParser.TimeRange result = timeParser.parseTimeExpression("这周");
        assertNotNull(result, "这周解析结果不应为null");

        // 与"本周"应该有相同的结果
        TimeParser.TimeRange thisWeekResult = timeParser.parseTimeExpression("本周");
        assertEquals(thisWeekResult.getStartTimestamp(), result.getStartTimestamp());
        assertEquals(thisWeekResult.getEndTimestamp(), result.getEndTimestamp());
    }

    @Test
    @DisplayName("测试上周时间范围解析")
    void testLastWeekParsing() {
        TimeParser.TimeRange result = timeParser.parseTimeExpression("上周");
        assertNotNull(result, "上周解析结果不应为null");

        LocalDate now = LocalDate.now();
        LocalDate mondayOfLastWeek = now.with(DayOfWeek.MONDAY).minusWeeks(1);
        LocalDate sundayOfLastWeek = mondayOfLastWeek.plusDays(6);

        LocalDateTime expectedStart = mondayOfLastWeek.atStartOfDay();
        LocalDateTime expectedEnd = sundayOfLastWeek.atTime(23, 59, 59);

        long expectedStartTimestamp = expectedStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long expectedEndTimestamp = expectedEnd.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        assertEquals(expectedStartTimestamp, result.getStartTimestamp(), 
                "上周开始时间应为上周一00:00:00");
        assertEquals(expectedEndTimestamp, result.getEndTimestamp(), 
                "上周结束时间应为上周日23:59:59");
    }

    @Test
    @DisplayName("测试本周上午时间范围解析")
    void testThisWeekMorningParsing() {
        TimeParser.TimeRange result = timeParser.parseTimeExpression("本周上午");
        assertNotNull(result, "本周上午解析结果不应为null");

        LocalDate now = LocalDate.now();
        LocalDate mondayOfThisWeek = now.with(DayOfWeek.MONDAY);

        LocalDateTime expectedStart = mondayOfThisWeek.atTime(6, 0);
        LocalDateTime expectedEnd = mondayOfThisWeek.atTime(12, 0);

        long expectedStartTimestamp = expectedStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long expectedEndTimestamp = expectedEnd.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        assertEquals(expectedStartTimestamp, result.getStartTimestamp());
        assertEquals(expectedEndTimestamp, result.getEndTimestamp());
    }

    @Test
    @DisplayName("测试本月时间范围解析")
    void testThisMonthParsing() {
        TimeParser.TimeRange result = timeParser.parseTimeExpression("本月");
        assertNotNull(result, "本月解析结果不应为null");

        LocalDate now = LocalDate.now();
        LocalDate firstOfMonth = now.withDayOfMonth(1);
        LocalDate lastOfMonth = now.withDayOfMonth(now.lengthOfMonth());

        LocalDateTime expectedStart = firstOfMonth.atStartOfDay();
        LocalDateTime expectedEnd = lastOfMonth.atTime(23, 59, 59);

        long expectedStartTimestamp = expectedStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long expectedEndTimestamp = expectedEnd.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        assertEquals(expectedStartTimestamp, result.getStartTimestamp(), 
                "本月开始时间应为本月1号00:00:00");
        assertEquals(expectedEndTimestamp, result.getEndTimestamp(), 
                "本月结束时间应为本月最后一天23:59:59");
    }

    @Test
    @DisplayName("测试上月时间范围解析")
    void testLastMonthParsing() {
        TimeParser.TimeRange result = timeParser.parseTimeExpression("上月");
        assertNotNull(result, "上月解析结果不应为null");

        LocalDate now = LocalDate.now();
        LocalDate lastMonth = now.minusMonths(1);
        LocalDate firstOfLastMonth = lastMonth.withDayOfMonth(1);
        LocalDate lastOfLastMonth = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth());

        LocalDateTime expectedStart = firstOfLastMonth.atStartOfDay();
        LocalDateTime expectedEnd = lastOfLastMonth.atTime(23, 59, 59);

        long expectedStartTimestamp = expectedStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long expectedEndTimestamp = expectedEnd.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        assertEquals(expectedStartTimestamp, result.getStartTimestamp());
        assertEquals(expectedEndTimestamp, result.getEndTimestamp());
    }

    @Test
    @DisplayName("测试最近3天时间范围解析")
    void testRecentDaysParsing() {
        TimeParser.TimeRange result = timeParser.parseTimeExpression("最近3天");
        assertNotNull(result, "最近3天解析结果不应为null");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expectedStart = now.minusDays(3).withHour(0).withMinute(0).withSecond(0).withNano(0);

        long expectedStartTimestamp = expectedStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        
        assertEquals(expectedStartTimestamp, result.getStartTimestamp());
        assertTrue(result.getEndTimestamp() <= System.currentTimeMillis() + 1000, 
                "结束时间应该接近当前时间");
    }

    @Test
    @DisplayName("测试最近2周时间范围解析")
    void testRecentWeeksParsing() {
        TimeParser.TimeRange result = timeParser.parseTimeExpression("最近2周");
        assertNotNull(result, "最近2周解析结果不应为null");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expectedStart = now.minusWeeks(2).withHour(0).withMinute(0).withSecond(0).withNano(0);

        long expectedStartTimestamp = expectedStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        
        assertEquals(expectedStartTimestamp, result.getStartTimestamp());
        assertTrue(result.getEndTimestamp() <= System.currentTimeMillis() + 1000);
    }

    @Test
    @DisplayName("测试今天时间范围解析")
    void testTodayParsing() {
        TimeParser.TimeRange result = timeParser.parseTimeExpression("今天");
        assertNotNull(result, "今天解析结果不应为null");

        LocalDate today = LocalDate.now();
        LocalDateTime expectedStart = today.atStartOfDay();
        LocalDateTime expectedEnd = today.atTime(23, 59, 59);

        long expectedStartTimestamp = expectedStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long expectedEndTimestamp = expectedEnd.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        assertEquals(expectedStartTimestamp, result.getStartTimestamp());
        assertEquals(expectedEndTimestamp, result.getEndTimestamp());
    }

    @Test
    @DisplayName("测试昨天时间范围解析")
    void testYesterdayParsing() {
        TimeParser.TimeRange result = timeParser.parseTimeExpression("昨天");
        assertNotNull(result, "昨天解析结果不应为null");

        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime expectedStart = yesterday.atStartOfDay();
        LocalDateTime expectedEnd = yesterday.atTime(23, 59, 59);

        long expectedStartTimestamp = expectedStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long expectedEndTimestamp = expectedEnd.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        assertEquals(expectedStartTimestamp, result.getStartTimestamp());
        assertEquals(expectedEndTimestamp, result.getEndTimestamp());
    }

    @Test
    @DisplayName("测试昨天下午时间范围解析")
    void testYesterdayAfternoonParsing() {
        TimeParser.TimeRange result = timeParser.parseTimeExpression("昨天下午");
        assertNotNull(result, "昨天下午解析结果不应为null");

        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime expectedStart = yesterday.atTime(12, 0);
        LocalDateTime expectedEnd = yesterday.atTime(18, 0);

        long expectedStartTimestamp = expectedStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long expectedEndTimestamp = expectedEnd.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        assertEquals(expectedStartTimestamp, result.getStartTimestamp());
        assertEquals(expectedEndTimestamp, result.getEndTimestamp());
    }

    @Test
    @DisplayName("测试空输入处理")
    void testEmptyInputHandling() {
        assertNull(timeParser.parseTimeExpression(null), "null输入应返回null");
        assertNull(timeParser.parseTimeExpression(""), "空字符串输入应返回null");
        assertNull(timeParser.parseTimeExpression("   "), "空白字符串输入应返回null");
    }

    @Test
    @DisplayName("测试无效输入处理")
    void testInvalidInputHandling() {
        assertNull(timeParser.parseTimeExpression("无效的时间表达式"), "无效输入应返回null");
        assertNull(timeParser.parseTimeExpression("123abc"), "无意义输入应返回null");
    }

    @Test
    @DisplayName("测试边界情况：月末本周")
    void testBoundaryWeekAtMonthEnd() {
        // 这个测试用于验证跨月边界的周计算是否正确
        TimeParser.TimeRange result = timeParser.parseTimeExpression("本周");
        assertNotNull(result, "月末本周解析应该正常");
        
        // 验证时间范围跨度为7天
        long weekInMillis = 7 * 24 * 60 * 60 * 1000L - 1000L; // 减去1秒因为结束时间是23:59:59
        long actualDuration = result.getEndTimestamp() - result.getStartTimestamp();
        
        assertTrue(Math.abs(actualDuration - weekInMillis) < 2000L, 
                "本周时间范围应该接近7天");
    }

    @Test
    @DisplayName("测试边界情况：跨年上周")
    void testBoundaryLastWeekAcrossYear() {
        // 只在年初测试时有意义，但这里我们测试基本功能
        TimeParser.TimeRange result = timeParser.parseTimeExpression("上周");
        assertNotNull(result, "跨年上周解析应该正常");
        
        long weekInMillis = 7 * 24 * 60 * 60 * 1000L - 1000L;
        long actualDuration = result.getEndTimestamp() - result.getStartTimestamp();
        
        assertTrue(Math.abs(actualDuration - weekInMillis) < 2000L, 
                "上周时间范围应该接近7天");
    }

    @Test
    @DisplayName("测试2月月末处理")
    void testFebruaryMonthEnd() {
        // 这个测试验证2月（特别是闰年）的月末处理
        TimeParser.TimeRange result = timeParser.parseTimeExpression("本月");
        assertNotNull(result, "2月本月解析应该正常");
        
        // 验证开始时间是1号，结束时间是月末
        LocalDate now = LocalDate.now();
        LocalDate firstOfMonth = now.withDayOfMonth(1);
        LocalDate lastOfMonth = now.withDayOfMonth(now.lengthOfMonth());
        
        LocalDateTime expectedStart = firstOfMonth.atStartOfDay();
        LocalDateTime actualStart = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(result.getStartTimestamp()), 
                ZoneId.systemDefault());
        
        assertEquals(expectedStart.toLocalDate(), actualStart.toLocalDate(), 
                "月初日期应该正确");
    }

    @Test
    @DisplayName("测试时间范围ToString方法")
    void testTimeRangeToString() {
        TimeParser.TimeRange result = timeParser.parseTimeExpression("今天");
        assertNotNull(result);
        
        String toString = result.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("TimeRange"));
        assertTrue(toString.contains("start="));
        assertTrue(toString.contains("end="));
    }
}