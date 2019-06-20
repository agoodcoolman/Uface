package com.whzxw.uface.ether.http;

/**
 * 网络请求返回实体
 */
public class ResponseDeviceEntity {
    private String success;
    private String message;
    private Device result;

    public String getSuccess() {
        return success;
    }

    public void setSuccess(String success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Device getResult() {
        return result;
    }

    public void setResult(Device result) {
        this.result = result;
    }


    public class Device {
        String deviceNo;
        String deviceName;

        public String getDeviceNo() {
            return deviceNo;
        }

        public void setDeviceNo(String deviceNo) {
            this.deviceNo = deviceNo;
        }

        public String getDeviceName() {
            return deviceName;
        }

        public void setDeviceName(String deviceName) {
            this.deviceName = deviceName;
        }
    }
}
