FROM python:3.12-slim
WORKDIR /app
RUN apt-get update && apt-get install --no-install-recommends -y libgomp1 && rm -rf /var/lib/apt/lists/*
COPY requirements.txt requirements-dev.txt ./
RUN pip install --no-cache-dir -r requirements-dev.txt
COPY . .
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
CMD ["python", "-m", "pytest", "-q", "-p", "no:cacheprovider", "--junitxml=/results/ai.xml"]
