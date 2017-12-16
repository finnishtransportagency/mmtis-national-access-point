(ns ote.views.theme
  (:require [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.core :refer [get-mui-theme color]]
            [ote.ui.debug :as debug]
            [ote.ui.nprogress :as progress]
            [stylefy.core :as stylefy]
            [ote.style.base :as style-base]
            [reagent.core :as r]
            [ote.localization :refer [tr tr-key]]
            [ote.app.controller.front-page :as fp-controller]))

(defn- flash-message-error [e! msg]
  [ui/snackbar {:open (boolean msg)
        :message (or msg "")
        :body-style style-base/error-flash-message-body
        :auto-hide-duration 6000
        :action (tr [:common-texts :navigation-give-feedback])
        :on-action-touch-tap #(e! (fp-controller/->OpenNewTab "http://bit.ly/nap-palaute"))
        :on-request-close #(e! (fp-controller/->ClearFlashMessage))}])

(defn- flash-message [e! msg ]
  [ui/snackbar {:open (boolean msg)
               :message (or msg "")
               :body-style style-base/success-flash-message-body
               :auto-hide-duration 4000
               :on-request-close #(e! (fp-controller/->ClearFlashMessage))}])

(defonce debug-visible? (r/atom false))
(defonce debug-state-toggle-listener
  (do (.addEventListener
       js/window "keypress"
       (fn [e]
         (when (or (and (.-ctrlKey e) (= "d" (.-key e)))
                   (and (.-ctrlKey e) (= "b" (.-key e))))
           (swap! debug-visible? not))))
      true))


(defn- debug-state [app]
  [:span
   (when @debug-visible?
     [:div.row
      [debug/debug app]])])

(defn on-before-unload []
  (let [state (atom {})]
    {:component-will-mount
     #(set! (.-onbeforeunload js/window)
            (fn [evt]
              (let [{:keys [before-unload-message navigation-prompt-open?]} @state]
                ;; Don't show browser's onbeforeunload dialog if internal
                ;; prompt dialog is already open

                ;; NOTE: This contains a fix for IE11. IE11 probably fails to trigger on-navigate event if
                ;; our ->GoToUrl i.e. #(set! (.-location js/window) ...) is used. Which is turn fails to set
                ;; "navigation-prompt-open?" flag to true. This causes beforeunload prompt to trigger every time user
                ;; navigates out of OTE app. The fix below adds additional check for before-unload-message and
                ;; prevents triggering a browser prompt if the message is not set. Additionally the actual event object
                ;; and event cb return value are modified to make sure that no prompt is shown on IE11 when it shouldn't.
                ;; TODO: Fix the actual problems related to url changes on IE11. (or wontfix)
                (if (or navigation-prompt-open? (not before-unload-message))
                  (do
                    (js-delete evt "returnValue")
                    js/undefined)
                  (do
                    (when before-unload-message
                      (set! (.-returnValue evt) before-unload-message))
                    before-unload-message)))))
     :component-will-receive-props
     (fn [_ [_ _ app _]]
       (reset! state
               (select-keys app [:before-unload-message :navigation-prompt-open?])))}))

(defn navigation-prompt [e! msg confirm]
  (let [tr (tr-key [:dialog :navigation-prompt])]
    [ui/dialog {:title (tr :title)
                :modal true
                :open true
                :actions [(r/as-element
                           [ui/flat-button {:label (tr :stay)
                                            :primary true
                                            :on-click #(e! (fp-controller/->StayOnPage))}])
                          (r/as-element
                           [ui/flat-button {:label (tr :leave)
                                            :secondary true
                                            :on-click #(e! confirm)}])]}
     msg]))

(defn theme
  "App container that sets the theme and common elements like flash message."
  [e! app content]

  (progress/configure (tr [:common-texts :loading]))

  (r/create-class
   (merge
    (on-before-unload)
    {:reagent-render
     (fn [e! {msg :flash-message
              error-msg :flash-message-error
              query :query
              navigation-prompt-open? :navigation-prompt-open?
              navigation-confirm :navigation-confirm
              before-unload-message :before-unload-message
              :as app} content]
       [ui/mui-theme-provider
        {:mui-theme
         (get-mui-theme
          {:palette   {;; primary nav color - Also Focus color in text fields
                       :primary1-color (color :blue700)

                       ;; Hint color in text fields
                       :disabledColor  (color :grey900)

                       ;; Main text color
                       :text-color     (color :grey900)
                       }

           :button    {:labelColor "#fff"}
           ;; Change drop down list items selected color
           :menu-item {:selected-text-color (color :blue700)}})}
          [:span

             (when error-msg
               [flash-message-error e! error-msg])
             (when (or msg (:logged_in query))
                   [flash-message e! (or msg (tr [:common-texts :logged-in]))])
             content
             (when navigation-prompt-open?
               [navigation-prompt e! before-unload-message navigation-confirm])
             [debug-state app]]])})))
