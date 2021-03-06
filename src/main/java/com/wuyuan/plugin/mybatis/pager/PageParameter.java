package com.wuyuan.plugin.mybatis.pager;

import java.io.Serializable;

/**
 * Created by Tiejun Sun on 2016-04-21. 分页参数类
 * 
 * @author Tiejun Sun
 */
public class PageParameter implements Serializable {

	private static final long serialVersionUID = -8324693985921606090L;

	public static final int DEFAULT_PAGE_SIZE = 10;

	private int pageSize;
	private int currentPage;
	private int prePage;
	private int nextPage;
	private int totalPage;
	private int totalCount;

	public PageParameter() {
		this.currentPage = 1;
		this.pageSize = DEFAULT_PAGE_SIZE;
	}

	/**
	 *
	 * @param currentPage
	 * @param pageSize
	 */
	public PageParameter(int currentPage, int pageSize) {
		this.currentPage = currentPage;
		this.pageSize = pageSize;
	}

	public int getCurrentPage() {
		return currentPage;
	}

	public void setCurrentPage(int currentPage) {
		this.currentPage = currentPage;
	}

	public int getPageSize() {
		return pageSize;
	}

	public void setPageSize(int pageSize) {
		this.pageSize = pageSize;
	}

	public int getPrePage() {
		return prePage;
	}

	public void setPrePage(int prePage) {
		this.prePage = prePage;
	}

	public int getNextPage() {
		return nextPage;
	}

	public void setNextPage(int nextPage) {
		this.nextPage = nextPage;
	}

	public int getTotalPage() {
		return totalPage;
	}

	public void setTotalPage(int totalPage) {
		this.totalPage = totalPage;
	}

	public int getTotalCount() {
		return totalCount;
	}

	public void setTotalCount(int totalCount) {
		this.totalCount = totalCount;
	}

}
