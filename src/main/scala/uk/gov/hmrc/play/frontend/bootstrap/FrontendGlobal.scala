/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.play.frontend.bootstrap

import com.kenshoo.play.metrics.MetricsFilter
import org.joda.time.{DateTime, Duration}
import org.slf4j.MDC
import play.api._
import play.api.mvc._
import play.filters.csrf.CSRFFilter
import play.filters.headers.SecurityHeadersFilter
import uk.gov.hmrc.play.audit.filters.FrontendAuditFilter
import uk.gov.hmrc.play.audit.http.config.ErrorAuditingSettings
import uk.gov.hmrc.play.filters.frontend.{CSRFExceptionsFilter, DeviceIdFilter, HeadersFilter, SessionTimeoutFilter}
import uk.gov.hmrc.play.filters.{CacheControlFilter, MicroserviceFilterSupport, RecoveryFilter}
import uk.gov.hmrc.play.frontend.bootstrap.Routing.RemovingOfTrailingSlashes
import uk.gov.hmrc.play.frontend.filters.{DeviceIdCookieFilter, SecurityHeadersFilterFactory, SessionCookieCryptoFilter}
import uk.gov.hmrc.play.graphite.GraphiteConfig
import uk.gov.hmrc.play.http.logging.filters.FrontendLoggingFilter

trait FrontendFilters {

  def loggingFilter: FrontendLoggingFilter

  def securityFilter: SecurityHeadersFilter

  def frontendAuditFilter: FrontendAuditFilter

  def metricsFilter: MetricsFilter

  def deviceIdFilter : DeviceIdFilter

  def csrfFilter: CSRFFilter

  def sessionTimeoutFilter: SessionTimeoutFilter

  def csrfExceptionsFilter: CSRFExceptionsFilter

  protected def defaultFrontendFilters: Seq[EssentialFilter] = Seq(
    metricsFilter,
    HeadersFilter,
    SessionCookieCryptoFilter,
    deviceIdFilter,
    loggingFilter,
    frontendAuditFilter,
    sessionTimeoutFilter,
    csrfExceptionsFilter,
    csrfFilter,
    CacheControlFilter.fromConfig("caching.allowedContentTypes"),
    RecoveryFilter)

  def frontendFilters: Seq[EssentialFilter] = defaultFrontendFilters

}

abstract class DefaultFrontendGlobal
  extends GlobalSettings
  with FrontendFilters
  with GraphiteConfig
  with RemovingOfTrailingSlashes
  with Routing.BlockingOfPaths
  with ErrorAuditingSettings
  with ShowErrorPage
  with MicroserviceFilterSupport {

  lazy val appName = Play.current.configuration.getString("appName").getOrElse("APP NAME NOT SET")
  lazy val enableSecurityHeaderFilter = Play.current.configuration.getBoolean("security.headers.filter.enabled").getOrElse(true)
  lazy val loggerDateFormat: Option[String] = Play.current.configuration.getString("logger.json.dateformat")

  override lazy val deviceIdFilter = DeviceIdCookieFilter(appName, auditConnector)

  override def onStart(app: Application) {
    Logger.info(s"Starting frontend : $appName : in mode : ${app.mode}")
    MDC.put("appName", appName)
    loggerDateFormat.foreach(str => MDC.put("logger.json.dateformat", str))
    super.onStart(app)
  }

  def filters = if (enableSecurityHeaderFilter) Seq(securityFilter) ++ frontendFilters  else frontendFilters

  override def doFilter(a: EssentialAction): EssentialAction =
    Filters(super.doFilter(a), filters: _* )

  override def securityFilter: SecurityHeadersFilter = SecurityHeadersFilterFactory.newInstance

  override def csrfFilter: CSRFFilter = CSRFFilter()

  override def metricsFilter: MetricsFilter = Play.current.injector.instanceOf[MetricsFilter]

  override def sessionTimeoutFilter: SessionTimeoutFilter = {
    val defaultTimeout = Duration.standardMinutes(15)
    val timeoutDuration = Play.current.configuration.getLong("session.timeoutSeconds")
      .map(Duration.standardSeconds)
      .getOrElse(defaultTimeout)

    new SessionTimeoutFilter(timeoutDuration = timeoutDuration)
  }

  override def csrfExceptionsFilter: CSRFExceptionsFilter = {
    val uriWhiteList =
      Play.current.configuration
        .getStringSeq("csrfexceptions.whitelist")
        .getOrElse(Seq.empty).toSet

    new CSRFExceptionsFilter(uriWhiteList)
  }

}
