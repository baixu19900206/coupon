package top.devgo.coupon.core.task.smzdm;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;

import top.devgo.coupon.core.page.Page;
import top.devgo.coupon.core.task.Task;
import top.devgo.coupon.core.task.TaskBase;
import top.devgo.coupon.utils.DateUtil;
import top.devgo.coupon.utils.JsonUtil;
import top.devgo.coupon.utils.MongoDBUtil;
import top.devgo.coupon.utils.TextUtil;

public class SMZDMTask extends TaskBase {

	private String timesort;
	private String mongoURI;
	private String dbName;
	
	
	/**
	 * 初始化smzdm首页抓取任务
	 * @param priority
	 * @param timesort 时间戳
	 * @param mongoURI "mongodb://localhost:27017,localhost:27018,localhost:27019"
	 * @param dbName
	 */
	public SMZDMTask(int priority, String timesort, String mongoURI, String dbName) {
		super(priority);
		this.timesort = timesort;
		this.mongoURI = mongoURI;
		this.dbName = dbName;
	}
	
	/**
	 * 初始化smzdm首页抓取任务
	 * @param timesort 时间戳
	 * @param mongoURI "mongodb://localhost:27017,localhost:27018,localhost:27019"
	 * @param dbName
	 */
	public SMZDMTask(String timesort, String mongoURI, String dbName) {
		this(1, timesort, mongoURI, dbName);
	}
	

	
	public HttpUriRequest buildRequest() {
		HttpUriRequest request = RequestBuilder
				.get()
				.setUri("http://www.smzdm.com/json_more")
				.addParameter("timesort", this.timesort)
				.setHeader("Host", "www.smzdm.com")
				.setHeader("Referer", "http://www.smzdm.com/")
				.setHeader(
						"User-Agent",
						"Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/47.0.2526.80 Safari/537.36")
				.setHeader("X-Requested-With", "XMLHttpRequest").build();
		return request;
	}


	@Override
	protected Page buildPage(CloseableHttpResponse response) {
		Page page = new Page();
		String htmlStr = getHtmlStr(response);
		page.setOriginalHtml(htmlStr);
		return page;
	}

	@Override
	protected void process(Page page) {
		String htmlStr = page.getOriginalHtml();
		htmlStr = TextUtil.decodeUnicode(htmlStr);
		htmlStr = JsonUtil.formateDoubleQuotationMarks(htmlStr);
		List<Map<String, String>> data = extractData(htmlStr);
		//格式化日期
		for (Map<String, String> d : data) {
			try {
				String date = DateUtil.getDateString(DateUtil.getDateFromString(d.get("article_date")));
				d.put("article_date_full", date);
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
		page.setData(data);
		
		//指定主键
		for (Map<String, String> d : data) {
			d.put("_id", d.get("article_id"));
		}
		MongoDBUtil.insertMany(data, this.mongoURI, this.dbName, "smzdm_data");
	}


	@Override
	protected List<Task> buildNewTask(Page page) {
		List<Map<String, String>> data = page.getData();
		List<Task> newTasks = new ArrayList<Task>();
		
		//增加相应的评论的抓取任务
		for (int i = 0; i < data.size(); i++) {
			String commentUrl = data.get(i).get("article_url");//http://www.smzdm.com/p/744743
			String productId = data.get(i).get("article_id");//744743
			SMZDMCommentTask commentTask = new SMZDMCommentTask(this.priority+1, productId, commentUrl+"/p1", this.mongoURI, this.dbName);
			newTasks.add(commentTask);
		}
		
		//增加新的主页抓取任务
		if (data.size() > 0) {
			int pos = data.size()-1;
			String timesort = data.get(pos).get("timesort");
			String article_date = data.get(pos).get("article_date");
			if(article_date.length() <= 5){//"article_date":"22:31",只要当日的
				SMZDMTask task = new SMZDMTask(this.priority, timesort, this.mongoURI, this.dbName);
				newTasks.add(task);
			}
		}
		return newTasks;
	}


	public String getTimesort() {
		return timesort;
	}

	public void setTimesort(String timesort) {
		this.timesort = timesort;
	}

	public String getMongoURI() {
		return mongoURI;
	}

	public void setMongoURI(String mongoURI) {
		this.mongoURI = mongoURI;
	}

	public String getDbName() {
		return dbName;
	}

	public void setDbName(String dbName) {
		this.dbName = dbName;
	}
}