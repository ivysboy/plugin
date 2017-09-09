# mybatis 分页拦截器用法

  1. 加载依赖
        
        <dependency>
            <groupId>com.happylifeplat</groupId>
            <artifactId>mybatis-pager-plugin</artifactId>
            <version>1.1-SNAPSHOT</version>
        </dependency>
        
  2. 在mybatis-config 中增加
  
            <properties>
                <property name="dialect" value="mysql" />
                <property name="pageSqlId" value=".*Page$" />
            </properties>
        
            <plugins>
                <plugin interceptor="PageInterceptor"></plugin>
            </plugins>
            
  3. 配置 Mapper 中要分页的 select id 满足拦截正则表达式 例如 上面配置中拦截以 Page 结尾的select 
           
            <select id="listPage"  resultMap="ListResultMap" parameterType="com.happylifeplat.coupon.model.GetCouponListPage">
                  select coupon.*,coupon_base.id as base_id
                      from coupon
                      left join coupon_base on coupon_base.id = coupon.base_info
                      <if test="filter!=null">
                        <trim prefix="WHERE" prefixOverrides="AND | OR">
                            <if test="filter.title !=null and filter.title!=''">
                                coupon_base.title like #{filter.title}
                            </if>
                            <if test="filter.beginDate!=null and filter.beginDate!=''">
                              AND coupon_base.begin_timestamp > #{filter.beginDate}
                            </if>
                        </trim>
                      </if>
            </select>
            
  4. 查询参数中增加 PageParameter 注意 属性名称必须是 page,orderBy
  
            public class GetCouponListPage {
            
                private CouponPageFilter filter;
                private PageParameter page;
                private String orderBy; //默认名称正序排列，名称前添加‘-’ 倒序排列，例如 -createTime。注意:实体必须是驼峰命名(createTime)，
                                            //数据库必须是下划线分隔(create_time)
                ....
             }
  
  
  
  