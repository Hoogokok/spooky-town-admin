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
                 ]
  :main ^:skip-aot spooky-town-admin.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})