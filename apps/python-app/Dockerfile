FROM python:3.13-alpine

ENV PYTHONUNBUFFERED=1

WORKDIR /app
COPY app.py .

RUN adduser -D -u 1000 appuser
USER appuser

EXPOSE 8080
CMD ["python", "app.py"]
