package com.kingdee.exam.controller.exam;

import com.kingdee.exam.entity.User;
import com.kingdee.exam.voj.exception.ResourceNotFoundException;
import com.kingdee.exam.voj.model.Language;
import com.kingdee.exam.voj.model.Problem;
import com.kingdee.exam.voj.model.Submission;
import com.kingdee.exam.voj.service.LanguageService;
import com.kingdee.exam.voj.service.ProblemService;
import com.kingdee.exam.voj.service.SubmissionService;
import com.kingdee.exam.util.CsrfProtector;
import com.kingdee.exam.util.HttpRequestParser;
import com.kingdee.exam.util.HttpSessionParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 处理用户的查看试题/提交评测等请求.
 */
@Controller
@RequestMapping(value="/p")
public class ProblemsController {
    @Autowired
    public ProblemsController(ProblemService problemService, SubmissionService submissionService, LanguageService languageService) {
        this.problemService = problemService;
        this.submissionService = submissionService;
        this.languageService = languageService;
    }

    /**
	 * 显示试题库中的全部试题.
	 * @param startIndex - 试题的起始下标
	 * @param keyword - 关键词
	 * @param problemCategorySlug - 试题分类的别名
	 * @param request - HttpRequest对象
	 * @return 包含试题库页面信息的ModelAndView对象
	 */
	@RequestMapping(value="", method=RequestMethod.GET)
	public ModelAndView problemsView(
            @RequestParam(value = "start", required = false, defaultValue = "1") long startIndex,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String problemCategorySlug,
            HttpServletRequest request) {
		long startIndexOfProblems = getFirstIndexOfProblems();
		if ( startIndex < startIndexOfProblems ) {
			startIndex = startIndexOfProblems;
		}
		
		List<Problem> problems = problemService.getProblemsUsingFilters(startIndex, keyword, problemCategorySlug,  true, NUMBER_OF_PROBLEMS_PER_PAGE);
		long totalProblems = problemService.getNumberOfProblemsUsingFilters(keyword, problemCategorySlug, true);
		ModelAndView view = new ModelAndView("problems/problems");
		view.addObject("problems", problems)
			.addObject("startIndexOfProblems", startIndexOfProblems)
			.addObject("numberOfProblemsPerPage", NUMBER_OF_PROBLEMS_PER_PAGE)
			.addObject("totalProblems", totalProblems)
			.addObject("problemCategories", problemService.getProblemCategoriesWithHierarchy());
		
		HttpSession session = request.getSession();
		if ( isLoggedIn(session) ) {
			long userId = (Long)session.getAttribute("uid");
			long endIndex = problemService.getLastIndexOfProblems(true, startIndex, NUMBER_OF_PROBLEMS_PER_PAGE);
			Map<Long, Submission> submissionOfProblems = submissionService.getSubmissionOfProblems(userId, startIndex, endIndex);
			view.addObject("submissionOfProblems", submissionOfProblems);
		}
		return view;
	}
	
	/**
	 * 获取试题的起始编号.
	 * @return 试题的起始编号
	 */
	private long getFirstIndexOfProblems() {
		return problemService.getFirstIndexOfProblems();
	}
	
	/**
	 * 获取试题列表.
	 * @param startIndex - 试题的起始下标
	 * @param request - HttpRequest对象
	 * @return 一个包含试题列表的HashMap对象
	 */
	@RequestMapping(value="/getProblems.action", method=RequestMethod.GET)
	public @ResponseBody
    Map<String, Object> getProblemsAction(
			@RequestParam(value="startIndex") long startIndex,
			@RequestParam(value="keyword", required = false) String keyword,
			@RequestParam(value="category", required = false) String problemCategorySlug,
			HttpServletRequest request) {
		HttpSession session = request.getSession();
		List<Problem> problems = problemService.getProblemsUsingFilters(startIndex, keyword, problemCategorySlug,  true, NUMBER_OF_PROBLEMS_PER_PAGE);
		Map<Long, Submission> submissionOfProblems = null;
		if ( isLoggedIn(session) ) {
			long userId = (Long)session.getAttribute("uid");
			submissionOfProblems = submissionService.
					getSubmissionOfProblems(userId, startIndex, startIndex + NUMBER_OF_PROBLEMS_PER_PAGE);
		}
		
		Map<String, Object> result = new HashMap<>(4, 1);
		result.put("isSuccessful", problems != null && !problems.isEmpty());
		result.put("problems", problems);
		result.put("submissionOfProblems", submissionOfProblems);
		return result;
	}
	
	/**
	 * 检查用户是否已经登录.
	 * @param session - HttpSession 对象
	 * @return 用户是否已经登录
	 */
	private boolean isLoggedIn(HttpSession session) {
		Boolean isLoggedIn = (Boolean)session.getAttribute("isLoggedIn");
        return isLoggedIn != null && isLoggedIn;
    }
	
	/**
	 * 加载试题的详细信息.
	 * @param problemId - 试题的唯一标识符
	 * @param request - HttpRequest对象
	 * @return 包含试题详细信息的ModelAndView对象
	 */
	@RequestMapping(value="/{problemId}", method=RequestMethod.GET)
	public ModelAndView problemView(
            @PathVariable("problemId") long problemId,
            HttpServletRequest request) {
		HttpSession session = request.getSession();
		boolean isLoggedIn = isLoggedIn(session);
		Problem problem = problemService.getProblem(problemId);
		
		if ( problem == null ) {
			throw new ResourceNotFoundException();
		}
		
		ModelAndView view = new ModelAndView("problems/problem");
		view.addObject("problem", problem);
		if ( isLoggedIn ) {
			long userId = (Long)session.getAttribute("uid");
			Map<Long, Submission> submissionOfProblems = submissionService.getSubmissionOfProblems(userId, problemId, problemId + 1);
			List<Submission> submissions = submissionService.getSubmissionUsingProblemIdAndUserId(problemId, userId, NUMBER_OF_SUBMISSIONS_PER_PROBLEM);
			List<Language> languages = languageService.getAllLanguages();
			
			view.addObject("latestSubmission", submissionOfProblems);
			view.addObject("submissions", submissions);
			view.addObject("languages", languages);
			view.addObject("csrfToken", CsrfProtector.getCsrfToken(session));
		}
		return view;
	}
	
	/**
	 * 加载试题题解页面.
	 * @param problemId - 试题的唯一标识符
	 * @return 包含试题题解信息的ModelAndView对象
	 */
	@RequestMapping(value="/{problemId}/solution", method=RequestMethod.GET)
	public ModelAndView solutionView(
            @PathVariable("problemId") long problemId) {
        return new ModelAndView("discussion/thread");
	}
	
	/**
	 * 创建提交.
	 * @param problemId - 试题的唯一标识符
	 * @param languageSlug - 编程语言的别名
	 * @param code - 代码
	 * @param request - HttpRequest对象
	 * @return 一个包含提交记录创建结果的Map<String, Object>对象
	 */
	@PostMapping("/createSubmission.action")
	public @ResponseBody
    Map<String, Object> createSubmissionAction(
			@RequestParam(value="problemId") long problemId,
			@RequestParam(value="languageSlug") String languageSlug,
			@RequestParam(value="code") String code,
			HttpServletRequest request) {
		HttpSession session = request.getSession();
		String ipAddress = HttpRequestParser.getRemoteAddr(request);
		User currentUser = HttpSessionParser.getCurrentUser(session);

		Map<String, Object> result = submissionService.createSubmission(currentUser, problemId, languageSlug, code);
		boolean isSuccessful = (Boolean)result.get("isSuccessful");
		if ( isSuccessful ) {
			long submissionId = (Long)result.get("submissionId");
			LOGGER.info(String.format("User: {%s} submitted code with SubmissionId #%s at %s",
					currentUser, submissionId, ipAddress));
		}
		return result;
	}
	
	/**
	 * 每次请求所加载试题数量.
	 */
	private static final int NUMBER_OF_PROBLEMS_PER_PAGE = 100;
	
	/**
	 * 每个试题加载最近提交的数量.
	 */
	private static final int NUMBER_OF_SUBMISSIONS_PER_PROBLEM = 10;


	private final ProblemService problemService;
	
	private final SubmissionService submissionService;
	
	private final LanguageService languageService;

	private static final Logger LOGGER = LogManager.getLogger(ProblemsController.class);
}
