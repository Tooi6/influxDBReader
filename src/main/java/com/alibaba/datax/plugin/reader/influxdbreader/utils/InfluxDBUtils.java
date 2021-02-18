package com.alibaba.datax.plugin.reader.influxdbreader.utils;

import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.plugin.reader.influxdbreader.InfluxDBReaderErrCode;
import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * @author Tooi
 * @date 2020/8/14 17:46
 * @description
 */
public class InfluxDBUtils {
    private static final Logger LOG = LoggerFactory.getLogger(InfluxDBUtils.class);

    private String endpoint;
    private String username;
    private String password;
    private String database;
    private String measurement;
    private String retentionPolicy = "autogen";
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


    InfluxDB influxDB;

    public InfluxDBUtils(String endpoint, String username, String password, String database) {
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
        influxDB = InfluxDBFactory.connect(endpoint, username, password);
        influxDB.enableBatch(BatchOptions.DEFAULTS);
        try {
            createDB(database);
            influxDB.setDatabase(database);
        } catch (Exception e) {
            throw DataXException.asDataXException(InfluxDBReaderErrCode.RUNTIME_EXCEPTION,
                    e.getMessage());
        } finally {
            influxDB.setRetentionPolicy(this.retentionPolicy);
        }
    }

    private void createDB(String database) {
        influxDB.query(new Query("CREATE DATABASE " + database));
    }

    public List<List<Object>> queryBySql(String sql) {
        List<List<Object>> result;
        QueryResult queryResult = influxDB.query(new Query(sql));
        // 查询结果验证
        if (queryResult.getError() != null) {
            throw DataXException.asDataXException(InfluxDBReaderErrCode.RUNTIME_EXCEPTION, "读取失败！error：" + queryResult.getError());
        } else if (queryResult.getResults() == null || queryResult.getResults().size() <= 0 || queryResult.getResults().get(0).getSeries() == null) {
            LOG.info("SQL:{},数据为空", sql);
            return null;
        }
        result = queryResult.getResults().get(0).getSeries().get(0).getValues();
        return result;
    }


}
