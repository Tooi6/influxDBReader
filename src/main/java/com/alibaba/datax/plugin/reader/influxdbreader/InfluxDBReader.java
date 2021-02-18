package com.alibaba.datax.plugin.reader.influxdbreader;

import com.alibaba.datax.common.element.*;
import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.common.plugin.RecordSender;
import com.alibaba.datax.common.spi.Reader;
import com.alibaba.datax.common.util.Configuration;
import com.alibaba.datax.plugin.reader.influxdbreader.utils.InfluxDBUtils;
import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.StringUtils;
import org.influxdb.impl.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * @author Tooi
 * @date 2020/8/14 16:22
 * @description
 */
public class InfluxDBReader extends Reader {

    public static class Job extends Reader.Job {
        private static final Logger LOG = LoggerFactory.getLogger(Job.class);
        private Configuration originalConfig = null;

        private Integer splitIntervalS;
        private long startDate;
        private long endDate;


        @Override
        public void init() {
            // 验证配置参数
            this.originalConfig = super.getPluginJobConf();
            originalConfig.getNecessaryValue(Key.ENDPOINT, InfluxDBReaderErrCode.REQUIRED_VALUE);
            originalConfig.getNecessaryValue(Key.USERNAME, InfluxDBReaderErrCode.REQUIRED_VALUE);
            originalConfig.getNecessaryValue(Key.PASSWORD, InfluxDBReaderErrCode.REQUIRED_VALUE);
            originalConfig.getNecessaryValue(Key.DATABASE, InfluxDBReaderErrCode.REQUIRED_VALUE);
            originalConfig.getNecessaryValue(Key.MEASUREMENT, InfluxDBReaderErrCode.REQUIRED_VALUE);
            List<String> columns = originalConfig.getList(Key.COLUMN, String.class);
            if (columns == null || columns.isEmpty()) {
                throw DataXException.asDataXException(
                        InfluxDBReaderErrCode.REQUIRED_VALUE, String.format("您提供配置文件有误，[%s]是必填参数，不允许为空或者留白 .", Key.COLUMN));
            }
            for (String specifyKey : Constant.MUST_CONTAINED_SPECIFY_KEYS) {
                if (!columns.contains(specifyKey)) {
                    throw DataXException.asDataXException(
                            InfluxDBReaderErrCode.ILLEGAL_VALUE, String.format("您提供配置文件有误，[%s]必须包含 '__time__'参数 .", Key.COLUMN));
                }
            }

            this.splitIntervalS = originalConfig.getInt(Key.INTERVAL_DATE_TIME, 60);
            if (splitIntervalS <= 0) {
                throw DataXException.asDataXException(
                        InfluxDBReaderErrCode.ILLEGAL_VALUE, String.format("您提供配置文件有误，[%s]必须大于0 .", Key.INTERVAL_DATE_TIME));
            }

            SimpleDateFormat format = new SimpleDateFormat(Constant.DEFAULT_DATA_FORMAT);
            String beginTime = originalConfig.getNecessaryValue(Key.BEGIN_DATE_TIME, InfluxDBReaderErrCode.REQUIRED_VALUE);
            try {
                this.startDate = format.parse(beginTime).getTime();
            } catch (ParseException e) {
                throw DataXException.asDataXException(
                        InfluxDBReaderErrCode.ILLEGAL_VALUE, String.format("您提供配置文件有误，[%s]参数必须按照[%s]格式填写.", Key.BEGIN_DATE_TIME, Constant.DEFAULT_DATA_FORMAT));
            }

            String endTime = originalConfig.getNecessaryValue(Key.END_DATE_TIME, InfluxDBReaderErrCode.REQUIRED_VALUE);
            try {
                this.endDate = format.parse(endTime).getTime();
            } catch (ParseException e) {
                throw DataXException.asDataXException(
                        InfluxDBReaderErrCode.ILLEGAL_VALUE, String.format("您提供配置文件有误，[%s]参数必须按照[%s]格式填写.", Key.END_DATE_TIME, Constant.DEFAULT_DATA_FORMAT));
            }

            if (startDate > endDate) {
                throw DataXException.asDataXException(
                        InfluxDBReaderErrCode.ILLEGAL_VALUE, String.format("您提供配置文件有误，[%s]必须大于[%s].", Key.END_DATE_TIME, Key.BEGIN_DATE_TIME));
            }
        }

        @Override
        public List<Configuration> split(int adviceNumber) {
            ArrayList<Configuration> configurations = new ArrayList<>();

            // 按照 splitIntervalS 参数分割
            long start = this.startDate;
            long end = this.endDate;
            while (start < end) {
                Configuration clone = this.originalConfig.clone();
                clone.set(Key.BEGIN_DATE_TIME, start);
                start += splitIntervalS;
                if (start - 1 > end) {
                    clone.set(Key.END_DATE_TIME, end);
                } else {
                    clone.set(Key.END_DATE_TIME, start - 1);
                }
                configurations.add(clone);
                LOG.info("Configuration: {}", JSON.toJSONString(clone));
            }
            return configurations;
        }

        @Override
        public void destroy() {

        }
    }

    public static class Task extends Reader.Task {
        private static final Logger LOG = LoggerFactory.getLogger(InfluxDBReader.Task.class);
        private String endpoint;
        private String username;
        private String password;
        private String database;
        private String measurement;
        private long beginDate;
        private long endDate;
        private List<String> columns;

        private InfluxDBUtils influxDBUtils;

        @Override
        public void init() {
            Configuration jobConf = this.getPluginJobConf();
            // 连接参数
            this.endpoint = jobConf.getString(Key.ENDPOINT);
            this.username = jobConf.getString(Key.USERNAME);
            this.password = jobConf.getString(Key.PASSWORD);
            this.database = jobConf.getString(Key.DATABASE);
            this.measurement = jobConf.getString(Key.MEASUREMENT);

            //
            this.beginDate = jobConf.getLong(Key.BEGIN_DATE_TIME);
            this.endDate = jobConf.getLong(Key.END_DATE_TIME);
            this.columns = jobConf.getList(Key.COLUMN, String.class);

            this.influxDBUtils = new InfluxDBUtils(endpoint, username, password, database);
        }

        @Override
        public void startRead(RecordSender recordSender) {
            String sql = buildSql();
            List<List<Object>> Listvalues = influxDBUtils.queryBySql(sql);
            if (Listvalues != null) {
                for (List<Object> values : Listvalues) {
                    Record record = recordSender.createRecord();

                    int i = 0;// values 下标
                    for (String column : columns) {
                        Object value = values.get(i++);
                        // 特殊列转换
                        if (column.equals(Constant.UID_SPECIFY_KEY)) {
                            // 插入uuid
                            value = UUID.randomUUID().toString();
                            // 跳过该value（当前的value应该是下一个column的值）
                            i--;
                        } else if (column.equals(Constant.TIME_SPECIFY_KEY)) {
                            SimpleDateFormat format = new SimpleDateFormat(Constant.DEFAULT_DATA_FORMAT);
                            try {
                                format.format(new Date(TimeUtil.fromInfluxDBTimeFormat((String) value)));
                            } catch (Exception e) {
                                throw DataXException.asDataXException(InfluxDBReaderErrCode.RUNTIME_EXCEPTION, "[time]:{} 转换失败" + value.toString());
                            }
                        }

                        // 类型转换
                        if (value == null) {
                            record.addColumn(new StringColumn(null));
                        } else if (value instanceof Double) {
                            record.addColumn(new DoubleColumn((Double) value));
                        } else if (value instanceof Boolean) {
                            record.addColumn(new BoolColumn((Boolean) value));
                        } else if (value instanceof Date) {
                            record.addColumn(new DateColumn((Date) value));
                        } else if (value instanceof Integer) {
                            record.addColumn(new LongColumn((Integer) value));
                        } else if (value instanceof Long) {
                            record.addColumn(new LongColumn((Long) value));
                        } else if (value instanceof String) {
                            record.addColumn(new StringColumn((String) value));
                        } else {
                            throw DataXException.asDataXException(InfluxDBReaderErrCode.RUNTIME_EXCEPTION, "未知数据类型{} ," + value.toString());
                        }
                    }
                    recordSender.sendToWriter(record);
                }
            }


        }

        private String buildSql() {
            StringBuffer sql = new StringBuffer("select");
            List<String> influxIds = new ArrayList<>();

            // column
            for (int i = 0; i < columns.size(); i++) {
                String col = columns.get(i);
                if (col.equals(Constant.UID_SPECIFY_KEY)) {
                    // 跳过uuid字段
                    continue;
                } else if (col.equals(Constant.TIME_SPECIFY_KEY)) {
                    col = "time";
                }
                influxIds.add(col);
            }
            sql.append(" " + StringUtils.join(influxIds, ","));
            sql.append(" from \"" + measurement + "\" where 1=1");

            SimpleDateFormat format = new SimpleDateFormat(Constant.DEFAULT_DATA_FORMAT);
            sql.append(" and time>='" + format.format(new Date(beginDate)) + "'");
            sql.append(" and time<='" + format.format(new Date(endDate)) + "'");
            // TODO where

            return String.valueOf(sql);
        }

        @Override
        public void destroy() {

        }
    }


}
