package com.pricing.management.infrastructure.dataobject;

public class PricingSceneDO extends BaseDataObject {
    private String bizCode; private String sceneCode; private String sceneName; private String sceneDesc; private String factorScope; private String status;
    public String getBizCode() { return bizCode; } public void setBizCode(String value) { bizCode = value; }
    public String getSceneCode() { return sceneCode; } public void setSceneCode(String value) { sceneCode = value; }
    public String getSceneName() { return sceneName; } public void setSceneName(String value) { sceneName = value; }
    public String getSceneDesc() { return sceneDesc; } public void setSceneDesc(String value) { sceneDesc = value; }
    public String getFactorScope() { return factorScope; } public void setFactorScope(String value) { factorScope = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
}
