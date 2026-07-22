export interface AboutLinks {
  support: string;
  documentation: string;
  license: string;
}

export interface AboutResponse {
  appName: string;
  appVersion: string;
  buildVersion: string;
  javaVersion: string;
  springBootVersion: string;
  aiProvider: string;
  aiModel: string;
  cloudPlatform: string;
  links: AboutLinks;
}
