package com.ztgeo.suqian.filter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import com.ztgeo.suqian.common.CryptographyOperation;
import com.ztgeo.suqian.common.GlobalConstants;
import com.ztgeo.suqian.common.ZtgeoBizRuntimeException;
import com.ztgeo.suqian.common.ZtgeoBizZuulException;
import com.ztgeo.suqian.config.RedisOperator;
import com.ztgeo.suqian.entity.ag_datashare.ApiBaseInfo;
import com.ztgeo.suqian.entity.ag_datashare.UserKeyInfo;
import com.ztgeo.suqian.msg.CodeMsg;
import com.ztgeo.suqian.repository.ApiBaseInfoRepository;
import com.ztgeo.suqian.repository.ApiUserFilterRepository;
import com.ztgeo.suqian.repository.UserKeyInfoRepository;
import com.ztgeo.suqian.utils.StreamOperateUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.netflix.zuul.filters.support.FilterConstants;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static com.ztgeo.suqian.common.GlobalConstants.USER_REDIS_SESSION;


/**
 * 响应解密过滤器
 *
 * @author bianyidong
 * @version 2019-6-22
 */
@Component
public class ResponseSafeToDataFilter extends ZuulFilter {

    private static Logger log = LoggerFactory.getLogger(ResponseSafeToDataFilter.class);
    private String api_id;
    private String Symmetric_pubkeyapiUserIDJson;
    //    @Resource
//    private UserKeyInfoRepository userKeyInfoRepository;
//    @Autowired
//    private RedisOperator redis;
    @Resource
    private ApiUserFilterRepository apiUserFilterRepository;
    @Resource
    private ApiBaseInfoRepository apiBaseInfoRepository;


    @Override
    public String filterType() {
        return FilterConstants.POST_TYPE;
    }

    @Override
    public int filterOrder() {
        return 1;
    }

    @Override
    public boolean shouldFilter() {
        String className = this.getClass().getSimpleName();
        RequestContext ctx = RequestContext.getCurrentContext();
        HttpServletRequest request = ctx.getRequest();
        api_id = request.getHeader("api_id");
        int count = apiUserFilterRepository.countApiUserFiltersByFilterBcEqualsAndApiIdEquals(className, api_id);
        if (count > 0) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Object run() throws ZuulException {
        log.info("=================进入post返回安全解密过滤器,接收返回的数据=====================");
        InputStream inputStream = null;
        InputStream inputStreamOld = null;
        InputStream inputStreamNew = null;
        try {
            RequestContext ctx = RequestContext.getCurrentContext();
            inputStream = ctx.getResponseDataStream();
            String userID = ctx.getRequest().getHeader("from_user");
            String apiID = ctx.getRequest().getHeader("api_id");
            //获取记录主键ID(来自routing过滤器保存的上下文)
            Object recordID = ctx.get(GlobalConstants.RECORD_PRIMARY_KEY);
            Object accessClientIp = ctx.get(GlobalConstants.ACCESS_IP_KEY);
            if (Objects.equals(null, accessClientIp) || Objects.equals(null, recordID))
                throw new ZtgeoBizZuulException(CodeMsg.FAIL, "返回安全解密过滤器访问者IP或记录ID未获取到");
            //获取接收方机构的密钥
//            List<ApiBaseInfo> list = apiBaseInfoRepository.findApiBaseInfosByApiIdEquals(apiID);
//            ApiBaseInfo apiBaseInfo = list.get(0);
//            String apiUserID = redis.get(USER_REDIS_SESSION + ":" + apiBaseInfo.getApiOwnerId());
//            if (StringUtils.isBlank(apiUserID)) {
//                UserKeyInfo userKeyInfo = userKeyInfoRepository.findByUserRealIdEquals(apiBaseInfo.getApiOwnerId());
//                Symmetric_pubkeyapiUserIDJson = userKeyInfo.getSymmetricPubkey();
//                JSONObject setjsonObject = new JSONObject();
//                setjsonObject.put("Symmetric_pubkey", userKeyInfo.getSymmetricPubkey());
//                setjsonObject.put("Sign_secret_key", userKeyInfo.getSignSecretKey());
//                setjsonObject.put("Sign_pub_key", userKeyInfo.getSignPubKey());
//                setjsonObject.put("Sign_pt_secret_key", userKeyInfo.getSignPtSecretKey());
//                setjsonObject.put("Sign_pt_pub_key", userKeyInfo.getSignPtPubKey());
//                //存入Redis
//                redis.set(USER_REDIS_SESSION + ":" + apiBaseInfo.getApiOwnerId(), setjsonObject.toJSONString());
//            } else {
//                JSONObject getjsonObject = JSONObject.parseObject(apiUserID);
//                Symmetric_pubkeyapiUserIDJson = getjsonObject.getString("Symmetric_pubkey");
//                if (StringUtils.isBlank(Symmetric_pubkeyapiUserIDJson)) {
//                    throw new ZtgeoBizRuntimeException(CodeMsg.FAIL, "未查询到返回安全解密密钥信息");
//                }
//            }
            ApiBaseInfo apiBaseInfo = apiBaseInfoRepository.queryApiBaseInfoByApiId(apiID);
            Symmetric_pubkeyapiUserIDJson = apiBaseInfo.getSymmetricPubkey();

            String rspBody = ctx.getResponseBody();
            if (!Objects.equals(null, rspBody)) {
                JSONObject jsonObject = JSON.parseObject(rspBody);
                String data = jsonObject.get("data").toString();
                String sign = jsonObject.get("sign").toString();

                // 解密
                String rspDecryptData = CryptographyOperation.aesDecrypt(Symmetric_pubkeyapiUserIDJson, data);
                //重新加载到response中
                jsonObject.put("data", rspDecryptData);
                jsonObject.put("sign", sign);
                String newbody = jsonObject.toString();
                log.info("返回安全解密过滤器入库完成");
                ctx.setResponseBody(newbody);
            } else if (!Objects.equals(null, inputStream)) {
                // 获取返回的body
                ByteArrayOutputStream byteArrayOutputStream = StreamOperateUtils.cloneInputStreamToByteArray(inputStream);
                inputStreamOld = new ByteArrayInputStream(byteArrayOutputStream.toByteArray()); // 原始流
                inputStreamNew = new ByteArrayInputStream(byteArrayOutputStream.toByteArray()); // 复制流
                // 获取返回的body字符串
                String responseBody = StreamUtils.copyToString(inputStreamOld, StandardCharsets.UTF_8);
                if (Objects.equals(null, responseBody)) {
                    responseBody = "";
                    throw new ZtgeoBizZuulException(CodeMsg.FAIL, "返回安全解密过滤器响应报文未获取到");
                }
                JSONObject jsonresponseBody = JSON.parseObject(responseBody);
                String rspEncryptData = jsonresponseBody.get("data").toString();
                String rspSignData = jsonresponseBody.get("sign").toString();


                // 解密
                String rspDecryptData = CryptographyOperation.aesDecrypt(Symmetric_pubkeyapiUserIDJson, rspEncryptData);
                jsonresponseBody.put("data", rspDecryptData);
                jsonresponseBody.put("sign", rspSignData);
                String newbody = jsonresponseBody.toString();
                ctx.setResponseBody(newbody);
                log.info("返回安全解密过滤器入库完成");
                ctx.setResponseDataStream(inputStreamNew);
            } else {
                log.info("返回安全解密过滤器记录完成");
            }
            ctx.set(GlobalConstants.RECORD_PRIMARY_KEY, recordID);
            ctx.set(GlobalConstants.ACCESS_IP_KEY, accessClientIp);
            return null;
        } catch (ZuulException z) {
            throw new ZtgeoBizZuulException(z, "返回安全解密过滤器post异常", z.nStatusCode, z.errorCause);
        } catch (Exception s) {
            throw new ZtgeoBizZuulException(s, CodeMsg.RSPDATA_ERROR, "内部异常");
        } finally {
            ResponseSafeToSignFilter.getFindlly(inputStream, inputStreamOld, inputStreamNew);
        }
    }
}
