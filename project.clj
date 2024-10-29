(defproject spooky-town-admin "0.1.0-SNAPSHOT"
  :description "만화와 영화 정보를 업데이트하는 CRUD 어드민"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [reagent "1.1.1"]
                 [re-frame "1.2.0"]
                 [compojure "1.6.2"]
                 [ring "1.9.5"]
                 [ring/ring-jetty-adapter "1.9.5"]
                 [ring/ring-json "0.5.1"]
                 [ring-cors "0.1.13"]
                 [environ "1.2.0"]
                 ;;clodinary
                 [com.cloudinary/cloudinary-core "1.36.0"]
                 [com.cloudinary/cloudinary-http44 "1.36.0"]
                 [com.cloudinary/cloudinary-taglib "1.36.0"]
                 ;;postgesql
                 [org.postgresql/postgresql "42.7.2"]
                 [com.github.seancorfield/next.jdbc "1.3.925"]
                 [com.github.seancorfield/honeysql "2.5.1103"]
                 ;;migratus 마이그레이션 라이브러리
                 [migratus "1.5.6"]
                 [hikari-cp "3.0.1"]            ;; hikari-cp 추가

                 [org.slf4j/slf4j-log4j12 "2.0.12"] ;; migratus의 로깅 의존성
                 [com.zaxxer/HikariCP "5.1.0"] ;; 데이터소스 풀링 라이브러리
                 ;; testcontainers 테스트 데이터베이스 라이브러리
                 [org.testcontainers/testcontainers "1.19.3"]
                 [org.testcontainers/postgresql "1.19.3"]
                 [org.testcontainers/jdbc "1.19.3"]
                 ;;로깅 
                 [org.clojure/tools.logging "1.2.4"]
                 [ch.qos.logback/logback-classic "1.4.11"]
                 ]
  ;;저장소 설정 추가
  :repositories [["central" "https://repo1.maven.org/maven2/"]
                 ["clojars" "https://repo.clojars.org/"]]
  :main ^:skip-aot spooky-town-admin.core
  :target-path "target/%s"
  :profiles {:dev {:dependencies [[org.slf4j/slf4j-log4j12 "2.0.12"]
                                  [log4j/log4j "1.2.17"]]
                   :env {:environment "dev"}
                   :resource-paths ["resources" "test/resources"]}
             :test {:dependencies [[org.slf4j/slf4j-log4j12 "2.0.12"]
                                   [log4j/log4j "1.2.17"]]
                    :resource-paths ["resources" "test/resources"]
                    :env {:environment "test"}}
             :prod {:env {:environment "prod"}}}
  :plugins [[lein-environ "1.2.0"]])  ;; environ 플러그인 추가 