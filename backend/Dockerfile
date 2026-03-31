FROM node:20-bookworm

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies (canvas will auto-detect system libraries)
RUN npm install

# Copy source code
COPY . .

# Build the application
RUN npm run build

# Expose port
EXPOSE 8080

# Start the server
CMD ["npm", "start"]
