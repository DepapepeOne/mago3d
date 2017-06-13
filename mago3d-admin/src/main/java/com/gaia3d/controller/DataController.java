package com.gaia3d.controller;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import com.gaia3d.domain.CacheManager;
import com.gaia3d.domain.CommonCode;
import com.gaia3d.domain.FileInfo;
import com.gaia3d.domain.DataGroup;
import com.gaia3d.domain.DataInfo;
import com.gaia3d.domain.Pagination;
import com.gaia3d.domain.Policy;
import com.gaia3d.domain.UserSession;
import com.gaia3d.service.FileService;
import com.gaia3d.service.DataGroupService;
import com.gaia3d.service.DataService;
import com.gaia3d.util.DateUtil;
import com.gaia3d.util.FileUtil;
import com.gaia3d.util.StringUtil;
import com.gaia3d.validator.DataValidator;
import com.google.gson.Gson;

import lombok.extern.slf4j.Slf4j;

/**
 * Data
 * @author jeongdae
 *
 */
@Slf4j
@Controller
@RequestMapping("/data/")
public class DataController {
	
	// Data 일괄 등록
	private String EXCEL_OBJECT_UPLOAD_DIR;
	// Data 샘플 다운로드
	private String EXCEL_SAMPLE_DIR;
	
	@Resource(name="dataValidator")
	private DataValidator dataValidator;
	
	@Autowired
	private DataGroupService dataGroupService;
	@Autowired
	private DataService dataService;
	@Autowired
	private FileService fileService;
	
	/**
	 * Data 목록
	 * @param request
	 * @param dataInfo
	 * @param pageNo
	 * @param list_counter
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "list-data.do")
	public String listData(	HttpServletRequest request, DataInfo dataInfo, @RequestParam(defaultValue="1") String pageNo, Model model) {
		
		log.info("@@ dataInfo = {}", dataInfo);
		DataGroup dataGroup = new DataGroup();
		dataGroup.setUse_yn(DataGroup.IN_USE);
		List<DataGroup> dataGroupList = dataGroupService.getListDataGroup(dataGroup);
		if(dataInfo.getData_group_id() == null) {
			dataInfo.setData_group_id(Long.valueOf(0l));
		}
		if(StringUtil.isNotEmpty(dataInfo.getStart_date())) {
			dataInfo.setStart_date(dataInfo.getStart_date().substring(0, 8) + DateUtil.START_TIME);
		}
		if(StringUtil.isNotEmpty(dataInfo.getEnd_date())) {
			dataInfo.setEnd_date(dataInfo.getEnd_date().substring(0, 8) + DateUtil.END_TIME);
		}

		long totalCount = dataService.getDataTotalCount(dataInfo);
		Pagination pagination = new Pagination(request.getRequestURI(), getSearchParameters(dataInfo), totalCount, Long.valueOf(pageNo).longValue(), dataInfo.getList_counter());
		log.info("@@ pagination = {}", pagination);
		
		dataInfo.setOffset(pagination.getOffset());
		dataInfo.setLimit(pagination.getPageRows());
		List<DataInfo> dataList = new ArrayList<DataInfo>();
		if(totalCount > 0l) {
			dataList = dataService.getListData(dataInfo);
		}
		
		boolean txtDownloadFlag = false;
		if(totalCount > 60000l) {
			txtDownloadFlag = true;
		}
		
		CommonCode dataInsertType = CacheManager.getCommonCode(CommonCode.OBJECT_REGISTER);
		CommonCode externalDataInsertType = CacheManager.getCommonCode(CommonCode.EXTERNAL_OBJECT_REGISTER);
		
		model.addAttribute(pagination);
		model.addAttribute("dataInsertType", dataInsertType);
		model.addAttribute("externalDataInsertType", externalDataInsertType);
		model.addAttribute("dataGroupList", dataGroupList);
		model.addAttribute("txtDownloadFlag", Boolean.valueOf(txtDownloadFlag));
		model.addAttribute("dataList", dataList);
		model.addAttribute("excelDataInfo", dataInfo);
		return "/data/list-data";
	}
	
	/**
	 * Data 그룹 등록된 Data 목록
	 * @param request
	 * @return
	 */
	@RequestMapping(value = "ajax-list-data-group-data.do", produces = "application/json; charset=utf8")
	@ResponseBody
	public String ajaxListDataGroupData(HttpServletRequest request, @RequestParam("data_group_id") Long data_group_id, @RequestParam(defaultValue="1") String pageNo) {
		Gson gson = new Gson();
		Map<String, Object> jSONData = new HashMap<String, Object>();
		String result = "success";
		Pagination pagination = null;
		List<DataInfo> dataList = new ArrayList<DataInfo>();
		try {		
			DataInfo dataInfo = new DataInfo();
			dataInfo.setData_group_id(data_group_id);
			
			long totalCount = dataService.getDataTotalCount(dataInfo);
			pagination = new Pagination(request.getRequestURI(), getSearchParameters(dataInfo), totalCount, Long.valueOf(pageNo).longValue());
			
			dataInfo.setOffset(pagination.getOffset());
			dataInfo.setLimit(pagination.getPageRows());
			if(totalCount > 0l) {
				dataList = dataService.getListData(dataInfo);
			}
		} catch(Exception e) {
			e.printStackTrace();
			result = "db.exception";
		}
		
		jSONData.put("result", result);
		jSONData.put("pagination", pagination);
		jSONData.put("dataList", dataList);
		
		log.info(">>>>>>>>>>>>>>>>>> datalist = {}", gson.toJson(jSONData));
		
		return gson.toJson(jSONData);
	}
	
	/**
	 * Data 그룹 전체 Data 에서 선택한 Data 그룹에 등록된 Data 를 제외한 Data 목록
	 * @param request
	 * @return
	 */
	@RequestMapping(value = "ajax-list-except-data-group-data-by-group-id.do")
	@ResponseBody
	public String ajaxListExceptDataGroupDataByGroupId(HttpServletRequest request, DataInfo dataInfo, @RequestParam(defaultValue="1") String pageNo) {
		Gson gson = new Gson();
		Map<String, Object> jSONData = new HashMap<String, Object>();
		String result = "success";
		Pagination pagination = null;
		List<DataInfo> dataList = new ArrayList<DataInfo>();
		try {
			long totalCount = dataService.getExceptDataGroupDataByGroupIdTotalCount(dataInfo);
			pagination = new Pagination(request.getRequestURI(), getSearchParameters(dataInfo), totalCount, Long.valueOf(pageNo).longValue());
			
			dataInfo.setOffset(pagination.getOffset());
			dataInfo.setLimit(pagination.getPageRows());
			if(totalCount > 0l) {
				dataList = dataService.getListExceptDataGroupDataByGroupId(dataInfo);
			}
		} catch(Exception e) {
			e.printStackTrace();
			result = "db.exception";
		}
		jSONData.put("result", result);
		jSONData.put("pagination", pagination);
		jSONData.put("dataList", dataList);
		return gson.toJson(jSONData);
	}
	
	/**
	 * 선택한 Data 그룹에 등록된 Data 목록
	 * @param request
	 * @return
	 */
	@RequestMapping(value = "ajax-list-data-group-data-by-group-id.do")
	@ResponseBody
	public String ajaxListDataGroupDataByGroupId(HttpServletRequest request, DataInfo dataInfo, @RequestParam(defaultValue="1") String pageNo) {
		Gson gson = new Gson();
		Map<String, Object> jSONData = new HashMap<String, Object>();
		String result = "success";
		Pagination pagination = null;
		List<DataInfo> dataList = new ArrayList<DataInfo>();
		try {
			
			long totalCount = dataService.getDataTotalCount(dataInfo);
			pagination = new Pagination(request.getRequestURI(), getSearchParameters(dataInfo), totalCount, Long.valueOf(pageNo).longValue());
			
			dataInfo.setOffset(pagination.getOffset());
			dataInfo.setLimit(pagination.getPageRows());
			if(totalCount > 0l) {
				dataList = dataService.getListData(dataInfo);
			}
		} catch(Exception e) {
			e.printStackTrace();
			result = "db.exception";
		}
		jSONData.put("result", result);
		jSONData.put("pagination", pagination);
		jSONData.put("dataList", dataList);
		return gson.toJson(jSONData);
	}
	
	/**
	 * Data 등록 화면
	 * @param model
	 * @return
	 */
	@GetMapping(value = "input-data.do")
	public String inputData(Model model) {
		
		DataGroup dataGroup = new DataGroup();
		dataGroup.setUse_yn(DataGroup.IN_USE);
		List<DataGroup> dataGroupList = dataGroupService.getListDataGroup(dataGroup);
		
		Policy policy = CacheManager.getPolicy();
		DataInfo dataInfo = new DataInfo();
		
		model.addAttribute(dataInfo);
		model.addAttribute("policy", policy);
		model.addAttribute("dataGroupList", dataGroupList);
		return "/data/input-data";
	}
	
	/**
	 * Data 등록
	 * @param request
	 * @param dataInfo
	 * @return
	 */
	@PostMapping(value = "ajax-insert-data-info.do", produces = "application/json; charset=utf8")
	@ResponseBody
	public String ajaxInsertDataInfo(HttpServletRequest request, DataInfo dataInfo) {
		Gson gson = new Gson();
		Map<String, Object> jSONData = new HashMap<String, Object>();
		String result = "success";
		try {
			dataInfo.setMethod_mode("insert");
			String errorcode = dataValidate(CacheManager.getPolicy(), dataInfo);
			if(errorcode != null) {
				result = errorcode;
				jSONData.put("result", result);
				return jSONData.toString();
			}
			
			int count = dataService.getDuplicationIdCount(dataInfo.getData_id());
			if(count > 0) {
				result = "data.id.duplication";
				jSONData.put("result", result);
				return jSONData.toString();
			}
			
			dataService.insertData(dataInfo);
		} catch(Exception e) {
			e.printStackTrace();
			result = "db.exception";
		}
	
		jSONData.put("result", result);
		
		return gson.toJson(jSONData);
	}
	
	/**
	 * 선택 Data 그룹내 Data 등록
	 * @param request
	 * @param data_all_id
	 * @param data_group_id
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "ajax-insert-data-group-data.do", method = RequestMethod.POST)
	@ResponseBody
	public String ajaxInsertDataGroupData(HttpServletRequest request,
			@RequestParam("data_group_id") Long data_group_id,
			@RequestParam("data_all_id") String[] data_all_id) {
		
		log.info("@@@ data_group_id = {}, data_all_id = {}", data_group_id, data_all_id);
		Gson gson = new Gson();
		Map<String, Object> jSONData = new HashMap<String, Object>();
		List<DataInfo> exceptDataList = new ArrayList<DataInfo>();
		List<DataInfo> dataList = new ArrayList<DataInfo>();
		String result = "success";
		try {
			if(data_group_id == null || data_group_id.longValue() == 0l ||				
					data_all_id == null || data_all_id.length < 1) {
				result = "input.invalid";
				jSONData.put("result", result);
				return jSONData.toString();
			}
			
			DataInfo dataInfo = new DataInfo();
			dataInfo.setData_group_id(data_group_id);
			dataInfo.setData_all_id(data_all_id);
			
			dataService.updateDataGroupData(dataInfo);
			
			dataList = dataService.getListData(dataInfo);
			exceptDataList = dataService.getListExceptDataGroupDataByGroupId(dataInfo);
		} catch(Exception e) {
			e.printStackTrace();
			jSONData.put("result", "db.exception");
		}
		
		jSONData.put("result", result	);
		jSONData.put("exceptDataList", exceptDataList);
		jSONData.put("dataList", dataList);
		return gson.toJson(jSONData);
	}
	
	/**
	 * ajax 용 Data validation 체크
	 * @param dataInfo
	 * @return
	 */
	private String dataValidate(Policy policy, DataInfo dataInfo) {
		
		// 비밀번호 변경이 아닐경우
		if(!"updatePassword".equals(dataInfo.getMethod_mode())) {
			if(dataInfo.getData_id() == null || "".equals(dataInfo.getData_id())) {
				return "data.input.invalid";
			}
			
			if(dataInfo.getData_group_id() == null || dataInfo.getData_group_id() <= 0
					|| dataInfo.getData_name() == null || "".equals(dataInfo.getData_name())) {
				return "data.input.invalid";
			}
		}
		
		return null;
	}
	
	/**
	 * Data 아이디 중복 체크
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "ajax-data-id-duplication-check.do", method = RequestMethod.POST)
	@ResponseBody
	public String ajaxDataIdDuplicationCheck(HttpServletRequest request, DataInfo dataInfo) {
		Gson gson = new Gson();
		Map<String, Object> jSONData = new HashMap<String, Object>();
		String result = "success";
		String duplication_value = "";
		try {
			if(dataInfo.getData_id() == null || "".equals(dataInfo.getData_id())) {
				result = "data.id.empty";
				jSONData.put("result", result);
				return jSONData.toString();
			}
			
			int count = dataService.getDuplicationIdCount(dataInfo.getData_id());
			log.info("@@ duplication_value = {}", count);
			duplication_value = String.valueOf(count);
		} catch(Exception e) {
			e.printStackTrace();
			result = "db.exception";
		}
	
		jSONData.put("result", result);
		jSONData.put("duplication_value", duplication_value);
		
		return gson.toJson(jSONData);
	}
	
	/**
	 * Data 정보
	 * @param data_id
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "detail-data.do")
	public String detailData(@RequestParam("data_id") String data_id, HttpServletRequest request, Model model) {
		
		String listParameters = getListParameters(request);
			
		DataInfo dataInfo =  dataService.getData(Long.valueOf(data_id));
		
		Policy policy = CacheManager.getPolicy();
		
		model.addAttribute("policy", policy);
		model.addAttribute("listParameters", listParameters);
		model.addAttribute("dataInfo", dataInfo);
		
		return "/data/detail-data";
	}
	
	/**
	 * Data 정보 수정 화면
	 * @param data_id
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "modify-data.do", method = RequestMethod.GET)
	public String modifyData(@RequestParam("data_id") String data_id, HttpServletRequest request, @RequestParam(defaultValue="1") String pageNo, Model model) {
		
		String listParameters = getListParameters(request);
		
		DataGroup dataGroup = new DataGroup();
		dataGroup.setUse_yn(DataGroup.IN_USE);
		List<DataGroup> dataGroupList = dataGroupService.getListDataGroup(dataGroup);
		DataInfo dataInfo =  dataService.getData(Long.valueOf(data_id));
		
		log.info("@@@@@@@@ dataInfo = {}", dataInfo);
		Policy policy = CacheManager.getPolicy();
		
		model.addAttribute("externalDataRegister", CacheManager.getCommonCode(CommonCode.EXTERNAL_OBJECT_REGISTER));
		model.addAttribute("listParameters", listParameters);
		model.addAttribute("policy", policy);
		model.addAttribute("dataGroupList", dataGroupList);
		model.addAttribute(dataInfo);
		
		return "/data/modify-data";
	}
	
	/**
	 * Data 정보 수정
	 * @param request
	 * @param dataInfo
	 * @return
	 */
	@RequestMapping(value = "ajax-update-data-info.do", method = RequestMethod.POST)
	@ResponseBody
	public String ajaxUpdateDataInfo(HttpServletRequest request, DataInfo dataInfo) {
		Gson gson = new Gson();
		Map<String, Object> jSONData = new HashMap<String, Object>();
		String result = "success";
		try {
			Policy policy = CacheManager.getPolicy();
			dataInfo.setMethod_mode("update");
			String errorcode = dataValidate(policy,dataInfo);
			if(errorcode != null) {
				result = errorcode;
				jSONData.put("result", result);
				return jSONData.toString();
			}
						
			dataService.updateData(dataInfo);
		} catch(Exception e) {
			e.printStackTrace();
			result = "db.exception";
		}
	
		jSONData.put("result", result);
		
		return gson.toJson(jSONData);
	}
	
	/**
	 * Data 상태 수정(패스워드 실패 잠금, 해제 등)
	 * @param request
	 * @param dataInfo
	 * @return
	 */
	@RequestMapping(value = "ajax-update-data-status.do", method = RequestMethod.POST)
	@ResponseBody
	public String ajaxUpdateDataStatus(	HttpServletRequest request, 
										@RequestParam("check_ids") String check_ids, 
										@RequestParam("business_type") String business_type, 
										@RequestParam("status_value") String status_value) {
		
		log.info("@@@@@@@ check_ids = {}, business_type = {}, status_value = {}", check_ids, business_type, status_value);
		Gson gson = new Gson();
		Map<String, Object> jSONData = new HashMap<String, Object>();
		String result = "success";
		String result_message = "";
		try {
			if(check_ids.length() <= 0) {
				jSONData.put("result", "check.value.required");
				return jSONData.toString();
			}
			List<String> dataList = dataService.updateDataStatus(business_type, status_value, check_ids);
			if(!dataList.isEmpty()) {
				int count = dataList.size();
				int i = 1;
				for(String dataId : dataList) {
					if(count == i) {
						result_message = result_message + dataId;
					} else {
						result_message = result_message + dataId + ", ";
					}
					i++;
				}
				
				String[] dataIds = check_ids.split(",");
				jSONData.put("update_count", dataIds.length - dataList.size());
				jSONData.put("business_type", business_type);
				jSONData.put("result_message", result_message);
			}
		} catch(Exception e) {
			e.printStackTrace();
			result = "db.exception";
		}
	
		jSONData.put("result", result);
		
		return gson.toJson(jSONData);
	}
	
	/**
	 * Data 삭제
	 * @param data_id
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "delete-data.do", method = RequestMethod.GET)
	public String deleteData(@RequestParam("data_id") String data_id, Model model) {
		
		// validation 체크 해야 함
		dataService.deleteData(Long.valueOf(data_id));
		return "redirect:/data/list-data.do";
	}
	
	/**
	 * 선택 Data 삭제
	 * @param request
	 * @param data_select_id
	 * @param data_group_id
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "ajax-delete-datas.do", method = RequestMethod.POST)
	@ResponseBody
	public String ajaxDeleteDatas(HttpServletRequest request, @RequestParam("check_ids") String check_ids) {
		
		log.info("@@@@@@@ check_ids = {}", check_ids);
		Gson gson = new Gson();
		Map<String, Object> jSONData = new HashMap<String, Object>();
		String result = "success";
		try {
			if(check_ids.length() <= 0) {
				jSONData.put("result", "check.value.required");
				return jSONData.toString();
			}
			
			dataService.deleteDataList(check_ids);
		} catch(Exception e) {
			e.printStackTrace();
			jSONData.put("result", "db.exception");
		}
		
		jSONData.put("result", result	);
		return gson.toJson(jSONData);
	}
	
	/**
	 * 선택 Data 그룹내 Data 삭제
	 * @param request
	 * @param data_select_id
	 * @param data_group_id
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "ajax-delete-data-group-data.do", method = RequestMethod.POST)
	@ResponseBody
	public String ajaxDeleteDataGroupData(HttpServletRequest request,
			@RequestParam("data_group_id") Long data_group_id,
			@RequestParam("data_select_id") String[] data_select_id) {
		
		log.info("@@@ data_group_id = {}, data_select_id = {}", data_group_id, data_select_id);
		Gson gson = new Gson();
		Map<String, Object> jSONData = new HashMap<String, Object>();
		List<DataInfo> exceptDataList = new ArrayList<DataInfo>();
		List<DataInfo> dataList = new ArrayList<DataInfo>();
		String result = "success";
		try {
			if(data_group_id == null || data_group_id.longValue() == 0l ||				
					data_select_id == null || data_select_id.length < 1) {
				result = "input.invalid";
				jSONData.put("result", result);
				return jSONData.toString();
			}
			
			DataInfo dataInfo = new DataInfo();
			dataInfo.setData_group_id(data_group_id);
			dataInfo.setData_select_id(data_select_id);
			
			dataService.updateDataGroupData(dataInfo);
			
			// UPDATE 문에서 data_group_id 를 temp 그룹으로 변경
			dataInfo.setData_group_id(data_group_id);
			
			dataList = dataService.getListData(dataInfo);
			exceptDataList = dataService.getListExceptDataGroupDataByGroupId(dataInfo);
		} catch(Exception e) {
			e.printStackTrace();
			jSONData.put("result", "db.exception");
		}
		
		jSONData.put("result", result	);
		jSONData.put("exceptDataList", exceptDataList);
		jSONData.put("dataList", dataList);
		return gson.toJson(jSONData);
	}
	
	/**
	 * Data 일괄 등록 화면
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "popup-input-excel-data.do", method = RequestMethod.GET)
	public String popupInputExcelData(Model model) {
		
		FileInfo fileInfo = new FileInfo();
		
		model.addAttribute("fileInfo", fileInfo);
		
		return "/data/popup-input-excel-data";
	}
	
	/**
	 * Data 일괄 등록
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "ajax-insert-excel-data.do", method = RequestMethod.POST)
	@ResponseBody
	public String ajaxInsertExcelData(MultipartHttpServletRequest request) {
		
		Gson gson = new Gson();
		Map<String, Object> jSONData = new HashMap<String, Object>();
		String result = "success";
		try {
			MultipartFile multipartFile = request.getFile("file_name");
			FileInfo fileInfo = FileUtil.uploadExcel(multipartFile, FileUtil.EXCEL_USER_UPLOAD, EXCEL_OBJECT_UPLOAD_DIR);
			if(fileInfo.getError_code() != null && !"".equals(fileInfo.getError_code())) {
				jSONData.put("result", fileInfo.getError_code());
				return jSONData.toString();
			}
			
			UserSession userSession = (UserSession)request.getSession().getAttribute(UserSession.KEY);
			fileInfo.setUser_id(userSession.getUser_id());
			
//			fileInfo = fileService.insertExcelData(fileInfo);
			
			jSONData.put("total_count", fileInfo.getTotal_count());
			jSONData.put("parse_success_count", fileInfo.getParse_success_count());
			jSONData.put("parse_error_count", fileInfo.getParse_error_count());
			jSONData.put("insert_success_count", fileInfo.getInsert_success_count());
			jSONData.put("insert_error_count", fileInfo.getInsert_error_count());
			
			// 파일 삭제
			File copyFile = new File(fileInfo.getFile_path() + fileInfo.getFile_real_name());
			if(copyFile.exists()) {
				copyFile.delete();
			}
		} catch(Exception e) {
			e.printStackTrace();
			result = "db.exception";
		}
	
		jSONData.put("result", result);
		
		return gson.toJson(jSONData);
	}
	
	/**
	 * Data Excel 다운로드
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "download-excel-data.do")
	public ModelAndView downloadExcelData(HttpServletRequest request, HttpServletResponse response, DataInfo dataInfo, Model model) {
		log.info("@@ dataInfo = {}", dataInfo);
		if(dataInfo.getData_group_id() == null) {
			dataInfo.setData_group_id(Long.valueOf(0l));
		}
		if(StringUtil.isNotEmpty(dataInfo.getStart_date())) {
			dataInfo.setStart_date(dataInfo.getStart_date().substring(0, 8) + DateUtil.START_TIME);
		}
		if(StringUtil.isNotEmpty(dataInfo.getEnd_date())) {
			dataInfo.setEnd_date(dataInfo.getEnd_date().substring(0, 8) + DateUtil.END_TIME);
		}
		
		long totalCount = 0l;
		List<DataInfo> dataList = new ArrayList<DataInfo>();
		try {
			// 논리적 삭제는 SELECT에서 제외
//			dataInfo.setDelete_flag(DataInfo.STATUS_LOGICAL_DELETE);
			totalCount = dataService.getDataTotalCount(dataInfo);
			long pageNo = 1l;
			long lastPage = 0l;
			long pagePerCount = 1000l;
			long pageListCount = 1000l;
			if(totalCount > 0l) {
				Pagination pagination = new Pagination(request.getRequestURI(), getSearchParameters(dataInfo), totalCount, pageNo, pagePerCount, pageListCount);
				lastPage = pagination.getLastPage();
				for(; pageNo<= lastPage; pageNo++) {
					pagination = new Pagination(request.getRequestURI(), getSearchParameters(dataInfo), totalCount, pageNo, pagePerCount, pageListCount);
					log.info("@@ pagination = {}", pagination);
					
					dataInfo.setOffset(pagination.getOffset());
					dataInfo.setLimit(pagination.getPageRows());
					List<DataInfo> subDataList = dataService.getListData(dataInfo);
					
					dataList.addAll(subDataList);
					
					Thread.sleep(3000);
				}
			}			
		} catch(Exception e) {
			e.printStackTrace();
		}
	
		ModelAndView modelAndView = new ModelAndView();
		modelAndView.setViewName("pOIExcelView");
		modelAndView.addObject("fileType", "USER_LIST");
		modelAndView.addObject("fileName", "USER_LIST");
		modelAndView.addObject("dataList", dataList);	
		return modelAndView;
	}
	
	/**
	 * Data Txt 다운로드
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "download-txt-data.do")
	@ResponseBody
	public String downloadTxtData(HttpServletRequest request, HttpServletResponse response, DataInfo dataInfo, Model model) {
		
		response.setContentType("application/force-download");
		response.setHeader("Content-Transfer-Encoding", "binary");
		response.setHeader("Content-Disposition", "attachment; filename=\"data_info.txt\"");
		
		log.info("@@ dataInfo = {}", dataInfo);
		if(dataInfo.getData_group_id() == null) {
			dataInfo.setData_group_id(Long.valueOf(0l));
		}
		if(StringUtil.isNotEmpty(dataInfo.getStart_date())) {
			dataInfo.setStart_date(dataInfo.getStart_date().substring(0, 8) + DateUtil.START_TIME);
		}
		if(StringUtil.isNotEmpty(dataInfo.getEnd_date())) {
			dataInfo.setEnd_date(dataInfo.getEnd_date().substring(0, 8) + DateUtil.END_TIME);
		}
		
		long totalCount = 0l;
		try {
			// 논리적 삭제는 SELECT에서 제외
//			dataInfo.setDelete_flag(DataInfo.STATUS_LOGICAL_DELETE);
			totalCount = dataService.getDataTotalCount(dataInfo);
			long pageNo = 1l;
			long lastPage = 0l;
			long pagePerCount = 1000l;
			long pageListCount = 1000l;
			long number = 1l;
			String SEPARATE = "|";
			String NEW_LINE = "\n";
			if(totalCount > 0l) {
				Pagination pagination = new Pagination(request.getRequestURI(), getSearchParameters(dataInfo), totalCount, pageNo, pagePerCount, pageListCount);
				lastPage = pagination.getLastPage();
				for(; pageNo<= lastPage; pageNo++) {
					pagination = new Pagination(request.getRequestURI(), getSearchParameters(dataInfo), totalCount, pageNo, pagePerCount, pageListCount);
					log.info("@@ pagination = {}", pagination);
					
					dataInfo.setOffset(pagination.getOffset());
					dataInfo.setLimit(pagination.getPageRows());
					List<DataInfo> dataList = dataService.getListData(dataInfo);
					
					int count = dataList.size();
					for(int j=0; j<count; j++) {
						DataInfo dbDataInfo = dataList.get(j);
						String data = number 
									+ SEPARATE + dbDataInfo.getData_group_name() + SEPARATE + dbDataInfo.getData_id()
									+ SEPARATE + dbDataInfo.getData_name()+ SEPARATE + dbDataInfo.getViewStatus()
									+ NEW_LINE;
						response.getWriter().write(data);
						number++;
					}
					Thread.sleep(3000);
				}
			}			
		} catch(Exception e) {
			e.printStackTrace();
		}
	
		return null;
	}
	
	/**
	 * Data 다운로드 Sample
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "download-excel-data-sample.do")
	@ResponseBody
	public void downloadExcelDataSample(HttpServletRequest request, HttpServletResponse response, Model model) {
		
		File rootDirectory = new File(EXCEL_SAMPLE_DIR);
		if(!rootDirectory.exists()) {
			rootDirectory.mkdir();
		}
				
		File file = new File(EXCEL_SAMPLE_DIR + "sample.xlsx");
		if(file.exists()) {
			String mimetype = "application/x-msdownload";
			response.setContentType(mimetype);
			String dispositionPrefix = "attachment; filename=";
			String encodedFilename = "sample.xlsx";

			response.setHeader("Content-Disposition", dispositionPrefix + encodedFilename);
			response.setContentLength((int)file.length());

			try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file)); BufferedOutputStream out = new BufferedOutputStream(response.getOutputStream())) {
				FileCopyUtils.copy(in, out);
				out.flush();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			response.setContentType("application/x-msdownload");
			try (PrintWriter printwriter = response.getWriter()) {
				printwriter.println("샘플 파일이 존재하지 않습니다.");
				printwriter.flush();
				printwriter.close();
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Data 그룹 정보
	 * @param request
	 * @return
	 */
	@RequestMapping(value = "ajax-data-group-info.do")
	@ResponseBody
	public String ajaxDataGroupInfo(HttpServletRequest request, @RequestParam("data_group_id") Long data_group_id) {
		
		log.info("@@@@@@@ data_group_id = {}", data_group_id);
		
		Gson gson = new Gson();
		Map<String, Object> jSONData = new HashMap<String, Object>();
		String result = "success";
		DataGroup dataGroup = null;
		try {	
			dataGroup = dataGroupService.getDataGroup(data_group_id);
			log.info("@@@@@@@ dataGroup = {}", dataGroup);
		} catch(Exception e) {
			e.printStackTrace();
			result = "db.exception";
		}
		jSONData.put("result", result);
		jSONData.put("dataGroup", dataGroup);
		
		return gson.toJson(jSONData);
	}
	
	/**
	 * 검색 조건
	 * @param dataInfo
	 * @return
	 */
	private String getSearchParameters(DataInfo dataInfo) {
		// TODO 아래 메소드랑 통합
		StringBuilder builder = new StringBuilder(100);
		builder.append("&");
		builder.append("search_word=" + StringUtil.getDefaultValue(dataInfo.getSearch_word()));
		builder.append("&");
		builder.append("search_option=" + StringUtil.getDefaultValue(dataInfo.getSearch_option()));
		builder.append("&");
		try {
			builder.append("search_value=" + URLEncoder.encode(StringUtil.getDefaultValue(dataInfo.getSearch_value()), "UTF-8"));
		} catch(Exception e) {
			e.printStackTrace();
			builder.append("search_value=");
		}
		builder.append("&");
		builder.append("data_group_id=" + dataInfo.getData_group_id());
		builder.append("&");
		builder.append("status=" + StringUtil.getDefaultValue(dataInfo.getStatus()));
		builder.append("&");
		builder.append("start_date=" + StringUtil.getDefaultValue(dataInfo.getStart_date()));
		builder.append("&");
		builder.append("end_date=" + StringUtil.getDefaultValue(dataInfo.getEnd_date()));
		builder.append("&");
		builder.append("order_word=" + StringUtil.getDefaultValue(dataInfo.getOrder_word()));
		builder.append("&");
		builder.append("order_value=" + StringUtil.getDefaultValue(dataInfo.getOrder_value()));
		builder.append("&");
		builder.append("list_count=" + dataInfo.getList_counter());
		return builder.toString();
	}
	
	/**
	 * 목록 페이지 이동 검색 조건
	 * @param request
	 * @return
	 */
	private String getListParameters(HttpServletRequest request) {
		StringBuilder builder = new StringBuilder(100);
		String pageNo = request.getParameter("pageNo");
		builder.append("pageNo=" + pageNo);
		builder.append("&");
		builder.append("search_word=" + StringUtil.getDefaultValue(request.getParameter("search_word")));
		builder.append("&");
		builder.append("search_option=" + StringUtil.getDefaultValue(request.getParameter("search_option")));
		builder.append("&");
		try {
			builder.append("search_value=" + URLEncoder.encode(StringUtil.getDefaultValue(request.getParameter("search_value")), "UTF-8"));
		} catch(Exception e) {
			e.printStackTrace();
			builder.append("search_value=");
		}
		builder.append("&");
		builder.append("data_group_id=" + request.getParameter("data_group_id"));
		builder.append("&");
		builder.append("status=" + StringUtil.getDefaultValue(request.getParameter("status")));
		builder.append("&");
		builder.append("start_date=" + StringUtil.getDefaultValue(request.getParameter("start_date")));
		builder.append("&");
		builder.append("end_date=" + StringUtil.getDefaultValue(request.getParameter("end_date")));
		builder.append("&");
		builder.append("order_word=" + StringUtil.getDefaultValue(request.getParameter("order_word")));
		builder.append("&");
		builder.append("order_value=" + StringUtil.getDefaultValue(request.getParameter("order_value")));
		builder.append("&");
		builder.append("list_count=" + StringUtil.getDefaultValue(request.getParameter("list_count")));
		return builder.toString();
	}
}