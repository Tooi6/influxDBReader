
# InfluxDbReader 插件文档

___


## 1 快速介绍

InfluxDbReader 实现了从 influxDB 读取数据。在底层实现上使用influxDB-java客户端连接 influxDB 并通过SQL语句，执行相应的SQL语句，将数据从SQL取出。


## 2 实现原理

简而言之，使用 influxDB-java 客户端建立远程连接 influxDB ，并根据用户配置生成对应的 SQL 语句，将数据从 influxDB 查询出来，并将查询结果封装成抽象数据集 Record 传递给 writer 处理


## 3 功能说明

### 3.1 配置样例

* influxDB 2 stream
```json
{
  "job": {
    "setting": {
      "speed": {
        "channel": 5
      },
      "errorLimit": {
        "record": 0,
        "percentage": 0.02
      }
    },
    "content": [
      {
        "reader": {
          "name": "influxdbreader",
          "parameter": {
            "endpoint": "http://192.168.142.135:8086",
            "username": "csb",
            "password": "123456",
            "database": "csb",
            "measurement": "SwDevice-Data",
            "column": [
              "__uid__",
              "__time__",
              "SN",
              "CCID",
              "ssyy",
              "status",
              "errHw",
              "errSw",
              "temperature",
              "humidity",
              "voltage",
              "battery"
            ],
            "splitIntervalS": 60000000,
            "beginDateTime": "2020-01-01 00:00:00",
            "endDateTime": "2021-01-01 00:00:00"
          }
        },
        "writer": {
          "name": "streamwriter",
          "parameter": {
            "print": false,
            "encoding": "UTF-8"
          }
        }
      }
    ]
  }
}

```

* influxDB 2 MySQL 
```json
{
  "job": {
    "setting": {
      "speed": {
        "channel": 5
      },
      "errorLimit": {
        "record": 500,
        "percentage": 0.05
      }
    },
    "content": [
      {
        "reader": {
          "name": "influxdbreader",
          "parameter": {
            "endpoint": "http://192.168.142.135:8086",
            "username": "csb",
            "password": "123456",
            "database": "csb",
            "measurement": "SwDevice-Data",
            "column": [
              "__uid__",
              "__time__",
              "SN",
              "CCID",
              "ssyy",
              "status",
              "errHw",
              "errSw",
              "temperature",
              "humidity",
              "voltage",
              "battery"
            ],
            "splitIntervalS": 60000000,
            "beginDateTime": "2020-01-01 00:00:00",
            "endDateTime": "2021-01-01 00:00:00"
          }
        },
        "writer": {
          "name": "mysqlwriter",
          "parameter": {
            "username": "csb",
            "password": "123456",
            "writeMode": "insert",
            "column": [
              "id",
              "createdatetime",
              "SN",
              "CCID",
              "ssyy",
              "status",
              "err_hw",
              "err_sw",
              "temperature",
              "humidity",
              "voltage",
              "battery"
            ],
            "session": [],
            "preSql": [],
            "connection": [
              {
                "jdbcUrl": "jdbc:mysql://127.0.0.1:3306/csb?useUnicode=true&characterEncoding=utf-8",
                "table": [
                  "s_ultrasound"
                ]
              }
            ]
          }
        }
      }
    ]
  }
}
```

### 3.2 参数说明
* **endpoint**

	* 描述：influxDB 的 http 连接地址，http://ip:port

	* 必选：是 <br />

	* 默认值：无 <br />

* **username**

	* 描述：数据源的用户名 <br />

	* 必选：是 <br />

	* 默认值：无 <br />
	
* **password**

	* 描述：数据源指定用户名的密码 <br />

	* 必选：是 <br />

	* 默认值：无 <br />
	
* **databases**

	* 描述：需要迁移的数据库 <br />

	* 必选：是 <br />

	* 默认值：无 <br />

* **measurement**

	* 描述：需要迁移的 measurement  <br />

	* 必选：是 <br />

	* 默认值：无 <br />
	
* **column**
    * 描述：配置需要迁移的列名集合，包含tag、field
        必须包含 "__time__" 字段，对应 influx 数据库的 time 字段
        支持特殊字段 "__uid__"，程序会生成一个uid（UUID.randomUUID().toString()）到该字段的位置（^_^ 项目需要）
    
    * 必选：是 <br />

    * 默认值：无 <br />

* **splitIntervalS**
    * 描述：用于 DataX 内部切分 Task ，单位秒（S），每个 Task 只查询设定好的时间段
        
    * 必选：是 <br />

    * 默认值：无 <br />
    
* **beginDateTime**
  * 描述：和 endDateTime 配合使用，用于指定哪个时间段内的数据点，需要被迁移
  
  * 必选：是
  
  * 格式：`yyyy-MM-dd HH:mm:ss`
  
  * 默认值：无

* **endDateTime**
  * 描述：和 beginDateTime 配合使用，用于指定哪个时间段内的数据点，需要被迁移
  
  * 必选：是
  
  * 格式：`yyyy-MM-dd HH:mm:ss`
  
  * 默认值：无

### 3.3 类型转换

下面列出influxDBReader针对influxDB类型转换表

| DataX 内部类型| influxDB 数据类型    |
| -------- | -----  |
| String   | String，null |
| Double   | Double |
| Boolean   | bool |
| Date   | time |
| Long   | int，long |

## 4 性能报告

### 4.1 环境准备

    * 注意：性能测试使用虚拟机当作远程influxDB服务器，MySQL则在宿主机运行，该测试并不严谨，如果需要更真实数据请自行测试

#### 4.1.1 数据特征
数据结构：
    
![测试数据结构.png](https://upload-images.jianshu.io/upload_images/18861083-033a01e75cd1a95f.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

#### 4.1.1 测试环境
* 机器参数：
    1. cpu：Intel(R) Core(TM) i3-9100 CPU @ 3.6GHz
    2. mem：8.00 GB
    3. disc：KINGSTON_SA400S3S1Z4
    
#### 4.1.3 DataX jvm 参数

    -Xms1g -Xmx1g -XX:+HeapDumpOnOutOfMemoryError

#### 4.2 测试结果

![测试结果.png](https://upload-images.jianshu.io/upload_images/18861083-f4b93427b9a92034.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
