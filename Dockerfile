FROM ruby:2.5.5-stretch
LABEL maintainer=someone@boystown.org

RUN apt-get update && apt-get install curl

ENV MARK_ENV="07-30-2019-t2"
ENV MARK_OTHER_ENV="11"
ENV MARK_OTHER_ENV2="222"
ENV MARK_OTHER_ENV3="aaaa"

