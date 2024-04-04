# JPDB Notify

A super simple app to create notifications when jpdb.io cards are ready for review.
Configuration options include:
- Setting minimum number of cards before a notification is created
- Interval between checks (minimum 15 minutes due to [WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work#schedule_periodic_work) api)

Note that the "Test notification" button uses your configuration, so nothing will show up if your number of cards ready for review is below your threshold.

Authentication opens an Android WebView where you can log in to jpdb.io as usual.
The app then grabs the authentication cookie and uses that in future requests.
