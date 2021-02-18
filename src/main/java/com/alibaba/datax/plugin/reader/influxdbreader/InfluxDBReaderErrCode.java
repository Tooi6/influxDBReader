package com.alibaba.datax.plugin.reader.influxdbreader;

import com.alibaba.datax.common.spi.ErrorCode;

/**
 * @author Tooi
 * @date 2020/8/14 16:50
 * @description
 */
public enum InfluxDBReaderErrCode implements ErrorCode {

    /**
     * 缺失必要的值
     */
    REQUIRED_VALUE("InfluxDBReader-00", "缺失必要的值"),
    /**
     * 值非法
     */
    ILLEGAL_VALUE("InfluxDBReader-01", "值非法"),
    /**
     * 运行时异常
     */
    RUNTIME_EXCEPTION("InfluxDBReader-02","运行时异常");


    private final String code;
    private final String description;

    InfluxDBReaderErrCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override
    public String getCode() {
        return this.code;
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public String toString() {
        return String.format("Code:[%s], Description:[%s]. ", this.code, this.description);
    }
}
