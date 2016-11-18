package com.app.web;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.SortedMap;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jdom2.JDOMException;
import org.json.JSONException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSONObject;
import com.app.dto.ViewBean;
import com.app.entity.PayLogBean;
import com.app.entity.PayWeixinBean;
import com.app.service.BaseServiceImpl;
import com.app.service.impl.PayWeixinServiceImpl;
import com.app.util.JsonUtils;
import com.app.util.SmallFunctionUtil_Used;
import com.app.util.SmallFunctionUtil;
import com.app.util.weixin.ConfigUtil;
import com.app.util.weixin.MapUtils;
import com.app.util.weixin.PayCommonUtil;
import com.app.util.weixin.WeixinConstant;
import com.app.validator.NameGroup;
import com.app.validator.TableAndObjGroup;
import com.app.validator.TableGroup;
import com.github.pagehelper.PageHelper;
import com.google.common.collect.ImmutableMap;

/**
 * 
 * {@code PayLogCtr} TODO
 * 
 * @author hesh
 * @time 2016年7月8日 - 下午3:15:09
 * 
 */
@RestController
@RequestMapping("/api/pl")
public class PayLogCtr extends BaseCtr {
	private static Logger logger = Logger.getLogger(PayLogCtr.class);  
	@Autowired
	private BaseServiceImpl service;
	@Autowired
	private PayWeixinServiceImpl payWeixinServiceImpl;

	@RequestMapping(value = "/{id}")
	public ViewBean view(@PathVariable("id") Long id) throws JSONException {
		return JsonUtils.getSuccess("获取成功", null);
	}
	/**
	 * 回调接口
	 * 
	 * @param request
	 * @return
	 * @throws JSONException
	 * @throws IOException 
	 * @throws JDOMException 
	 * @throws InvocationTargetException 
	 * @throws IllegalAccessException 
	 */
	@RequestMapping(value = "/callback")
	public String callback(HttpServletRequest request) throws Exception{
		try {
			return payWeixinServiceImpl.addCallback(request);
		} catch (Exception e) {
			return PayCommonUtil.setXML(WeixinConstant.FAIL,"weixin pay server exception");
		}
	}

	/**
	 * 
	 * TODO 添加信息
	 * 
	 * @Description
	 * @param record
	 * @param result
	 * @param errors
	 * @return
	 * @throws Exception
	 * @version 1.0 2016年4月1日-下午4:43:19
	 * @auther hesh
	 */
	@RequestMapping(value = "/add$pl")
	public ViewBean addPayLog(@Validated(TableGroup.class) PayLogBean record, 
			HttpServletRequest request,
			BindingResult result,Errors errors
			) throws Exception {
		if (result.hasErrors()) {
			return JsonUtils.getError(errors);
		}
		
		String ip = SmallFunctionUtil_Used.getIpAddr(request);
		record.setIp(ip);
		Long objId ;
		Character payWay = record.getPayWay();
		if(payWay == null || payWay == '0'){//积分支付
			Assert.hasLength(record.getToken(), "设备唯一号不能为空");
			record.setActionStatus("积分操作成功");
			/*if(service.save(record,false) > 0){
				logger.info("保存用户交易记录，操作人id： " + record.getUserId());
				objId = record.getObjId();
				return JsonUtils.getSuccess("交易成功");
			}*/
		}else{
			PayWeixinBean payWeixinBean = new PayWeixinBean();
			try {
				BeanUtils.copyProperties(record, payWeixinBean);//商品描述	body; 商品详情	detail; 附加数据 attach
			} catch (Exception e) {
			}
			payWeixinBean.setObjId(record.getObjId() + "");
			payWeixinBean.setUserId(record.getUserId());
			payWeixinBean.setTotal_fee(record.getMoney());
			payWeixinBean.setSpbill_create_ip(ip);
			payWeixinBean.setAttach(record.getOriginalTableName());
			
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
			Date d_now = new Date();
			String now = sdf.format(d_now);
			payWeixinBean.setOut_trade_no(("WX"+ now + SmallFunctionUtil.getSysJournalNo(14, false)).toUpperCase());
			
			payWeixinBean.setFee_type("CNY");
			payWeixinBean.setTrade_type("APP");
			
			payWeixinBean.setOrder_status(0);
			payWeixinBean.setOrderTime(System.currentTimeMillis() + "");
			
			record.setPayWay('1');//微信支付'
			record.setIsDelated('0');//暂存为无效记录，因为不一定支付成功，在订单查询或回调中修改该记录
			record.setCreateName(record.getUserId());
			record.setUpdateName(record.getUserId());
			record.setCreateTime(new java.sql.Timestamp(System.currentTimeMillis()));
			record.setUpdateTime(new java.sql.Timestamp(System.currentTimeMillis()));
			
			ViewBean datas = payWeixinServiceImpl.addUnifiedOrder(payWeixinBean,record);
			
			logger.info("保存用户交易记录，操作人id： " + record.getUserId());
			return datas;
		}
		
		
		logger.info("保存用户交易记录，操作人id： " + record.getUserId());
		return JsonUtils.getSuccess("保存成功");
	}
	

	@RequestMapping("/del$pl")
	public ViewBean deletePayLog(@Validated(NameGroup.class) PayLogBean record, BindingResult result,
			Errors errors
			) throws Exception{
		if (result.hasErrors()) {
			return JsonUtils.getError(errors);
		}
		int r = service.deleteByPrimaryKey(record.getId());
		if(r == 0){
			return JsonUtils.getError("请核对类别及相应类型");
		}
		return JsonUtils.getSuccess("删除成功");
	}
	
	/**
	 * 查询用户所有交易记录
	 * 
	 * 
	 * @param record
	 * @param result
	 * @param errors
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/getPayLogs")
	public ViewBean findPayLogs( @Validated(TableAndObjGroup.class) PayLogBean record, BindingResult result,
			Errors errors
			,@RequestParam(value = "pageNum",required = false) Integer pageNum
			,@RequestParam(value = "pageSize",required = false) Integer pageSize
			) throws Exception{
		if (result.hasErrors()) {
			return JsonUtils.getError(errors);
		}
		if(pageNum != null){
			if(pageSize == null){
				pageSize = 10;
			}
			PageHelper.startPage(pageNum, pageSize);
		}
		return JsonUtils.getSuccess("操作成功",service.find(record));
	}
	/**
	 * 查询微信支付结果
	 * 
	 * @param record
	 * @param result
	 * @param errors
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/get$WxPaySta")
	public ViewBean checkWeixinPay( @RequestParam("out_trade_no") String out_trade_no) throws Exception{
		logger.debug("调用微信订单查询接口...");
		logger.info("weixin_pay out_trade_no: " +  out_trade_no );
		SortedMap<String, Object> params = prepareQueryData(out_trade_no);
		logger.debug("查询订单的请求数据 ={}" + JSONObject.toJSONString(params));
		return payWeixinServiceImpl.addCheckOrderStatus(params);
	}
	
	/**
	 * 封装查询订单接口请求数据
	 * 
	 * @param outTradeNo 
	 * @param transactionId
	 * @return
	 */
	private SortedMap<String, Object> prepareQueryData(String outTradeNo) {
		Map<String, Object> queryParams = null;
		// 微信的订单号，优先使用
		queryParams = ImmutableMap.<String, Object> builder()
				.put("appid", ConfigUtil.APPID)
				.put("mch_id", ConfigUtil.MCH_ID)
				.put("out_trade_no", outTradeNo)
				.put("nonce_str", PayCommonUtil.CreateNoncestr()).build();
		// key ASCII 排序
		SortedMap<String, Object> sortMap = MapUtils.sortMap(queryParams);
		// 签名
		String createSign = PayCommonUtil.createSign("UTF-8", sortMap);
		sortMap.put("sign", createSign);
		return sortMap;
	}
	/**
	 * 微信退单
	 * 
	 * @param out_trade_no
	 * @param userId
	 * @param out_refund_no
	 * 		不为空，表示原来退单请求失败，重新发起的退单请求
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("get$WxRef")
	public ViewBean refundWeixinPay( @RequestParam(value="out_trade_no") String out_trade_no
			,@RequestParam("userId")String userId
			,@RequestParam(value = "out_refund_no",required = false)String out_refund_no) throws Exception{
		logger.debug("微信退单");
		if(StringUtils.isBlank(out_refund_no)){//
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
			Date d_now = new Date();
			String now = sdf.format(d_now);
			out_refund_no = ("WXTD"+ now + SmallFunctionUtil.getSysJournalNo(12, false)).toUpperCase();
		}
		return payWeixinServiceImpl.addRefundOrder(out_trade_no,out_refund_no,userId);
	}
	/**
	 * 查询微信退单
	 * 
	 * @param out_trade_no 
	 * 		我方订单单号
	 * 
	 * @param userId
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("get$WxRefQu")
	public ViewBean refundQuery( @RequestParam(value="out_trade_no") String out_trade_no,@RequestParam("userId")String userId) throws Exception{
		logger.debug("微信退单");
		return payWeixinServiceImpl.addRefundQuery(out_trade_no, userId);
	}
}
