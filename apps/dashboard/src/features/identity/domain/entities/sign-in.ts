export type SignInCredentials = Readonly<{ email: string; password: string }>;

export type IdentityProvider = Readonly<{
  id: string;
  name: string;
  authorizationPath: string;
}>;
